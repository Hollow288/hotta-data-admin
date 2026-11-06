package com.hollow.build.trading.paper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static com.hollow.build.trading.paper.PaperModels.*;

/**
 * 模拟账本：账户行锁保护同一账户，模型/HTTP 查询不在数据库事务内。
 * 完成时现金、持仓和运行结果在同一个事务提交，重试只读取已存在的运行。
 */
@Repository
public class PaperLedger {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PaperLedger(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Account create(long ownerId, String name, BigDecimal initialCash, Rules rules, long now) {
        String id = uuid();
        jdbc.update("""
                INSERT INTO trading_paper_account
                (account_id,owner_id,name,initial_cash_usdt,cash_usdt,fees_usdt,realized_pnl_usdt,version,rules_json,created_at_epoch_ms)
                VALUES (?,?,?,?,?,0,0,0,?,?)
                """, id, ownerId, name, initialCash, initialCash, JSON.toJSONString(rules), now);
        return account(ownerId, id);
    }

    public Account account(long ownerId, String id) {
        return loadAccount(ownerId, id, false);
    }

    public List<Account> accounts(long ownerId) {
        return jdbc.queryForList("SELECT account_id FROM trading_paper_account WHERE owner_id=? ORDER BY created_at_epoch_ms DESC LIMIT 50",
                        String.class, ownerId).stream().map(id -> account(ownerId, id)).toList();
    }

    public Run run(long ownerId, String accountId, String runId) {
        account(ownerId, accountId);
        return findRun(accountId, "run_id", runId);
    }

    public List<Run> runs(long ownerId, String accountId, int limit) {
        account(ownerId, accountId);
        return jdbc.query("SELECT * FROM trading_paper_run WHERE account_id=? ORDER BY started_at_epoch_ms DESC,run_id DESC LIMIT ?",
                RUN_MAPPER, accountId, limit);
    }

    public List<EquitySample> equitySamples(long ownerId, String accountId, int limit) {
        account(ownerId, accountId);
        return jdbc.query("""
                SELECT * FROM trading_paper_equity_sample WHERE account_id=?
                ORDER BY sample_sequence DESC,sample_id DESC LIMIT ?
                """, (rs, row) -> new EquitySample(rs.getString("sample_id"), rs.getLong("sample_sequence"),
                rs.getLong("as_of_epoch_ms"), rs.getBigDecimal("equity_usdt"), rs.getBigDecimal("btc_price_usdt")), accountId, limit);
    }

    public Reservation begin(long ownerId, String accountId, String key, long now) {
        return transaction.execute(status -> {
            Account account = loadAccount(ownerId, accountId, true);
            List<Run> previous = jdbc.query("SELECT * FROM trading_paper_run WHERE account_id=? AND idempotency_key=?",
                    RUN_MAPPER, accountId, key);
            if (!previous.isEmpty()) {
                Run replay = previous.getFirst();
                if (replay.status() == RunStatus.RUNNING && replay.runId().equals(account.activeRunId())
                        && now - replay.startedAtEpochMillis() >= replay.rules().runTimeoutMillis()) {
                    expire(account, replay, now);
                    return new Reservation(findRun(accountId, "run_id", replay.runId()), loadAccount(ownerId, accountId, false), false);
                }
                return new Reservation(replay, account, false);
            }
            if (account.activeRunId() != null) {
                Run active = findRun(accountId, "run_id", account.activeRunId());
                if (now - active.startedAtEpochMillis() < active.rules().runTimeoutMillis()) {
                    throw new PaperTradingException(409, "该账户已有运行中的实验，请复用原 idempotencyKey 查询结果或稍后重试");
                }
                expire(account, active, now);
            }
            String id = uuid();
            Snapshot before = PaperBroker.snapshot(account, null, Map.of(), now);
            jdbc.update("""
                    INSERT INTO trading_paper_run
                    (run_id,account_id,idempotency_key,status,rules_json,before_json,debug_trace_json,started_at_epoch_ms)
                    VALUES (?,?,?,'RUNNING',?,?,'[]',?)
                    """, id, accountId, key, JSON.toJSONString(account.rules()), JSON.toJSONString(before), now);
            jdbc.update("UPDATE trading_paper_account SET active_run_id=? WHERE account_id=?", id, accountId);
            return new Reservation(findRun(accountId, "run_id", id), account, true);
        });
    }

    public record Finalization(Account account, Run run, int tradesToday, long nowEpochMillis) {}

    /** 网络查询在事务外完成；只在仍持有执行权时保存决策实际看见的估值。 */
    public boolean recordBefore(long ownerId, String accountId, String runId, Snapshot snapshot, long now) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Account account = loadAccount(ownerId, accountId, true);
            Run run = findRun(accountId, "run_id", runId);
            if (!runId.equals(account.activeRunId()) || run.status() != RunStatus.RUNNING
                    || now - run.startedAtEpochMillis() >= run.rules().runTimeoutMillis()) return false;
            jdbc.update("UPDATE trading_paper_run SET before_json=? WHERE run_id=?", json(snapshot), runId);
            observe(accountId, runId + ":before", account.version() * 2, snapshot);
            return true;
        }));
    }

    public Run complete(long ownerId, String accountId, String runId, long now,
                        Function<Finalization, Completion> finalizer) {
        return complete(ownerId, accountId, runId, () -> now, finalizer);
    }

    public Run complete(long ownerId, String accountId, String runId, LongSupplier clock,
                        Function<Finalization, Completion> finalizer) {
        return transaction.execute(status -> {
            Account account = loadAccount(ownerId, accountId, true);
            long now = clock.getAsLong(); // 获取锁之后再检查时间，等待锁不能延长行情有效期。
            Run run = findRun(accountId, "run_id", runId);
            if (run.status() != RunStatus.RUNNING) return run;
            if (!runId.equals(account.activeRunId())) throw new PaperTradingException(409, "运行已失去账户执行权，拒绝提交");
            Completion completed;
            if (now - run.startedAtEpochMillis() >= run.rules().runTimeoutMillis()) {
                completed = new Completion(RunStatus.ERROR, null, null, PaperBroker.snapshot(account, null, Map.of(), now),
                        "运行超过时间上限，未产生模拟成交", List.of());
            } else {
                long start = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                        .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
                long end = Instant.ofEpochMilli(start).atZone(ZoneId.of("Asia/Shanghai")).plusDays(1).toInstant().toEpochMilli();
                int count = (int) jdbc.queryForList("""
                        SELECT fill_json FROM trading_paper_run WHERE account_id=? AND status='FILLED'
                        AND completed_at_epoch_ms>=? AND completed_at_epoch_ms<?
                        """, String.class, accountId, start, end).stream()
                        .map(value -> JSON.parseObject(value, Fill.class))
                        .filter(fill -> fill != null && fill.side() == TradeDecision.Action.BUY).count();
                completed = finalizer.apply(new Finalization(account, run, count, now));
            }
            Execution execution = completed.execution();
            if (completed.status() == RunStatus.RUNNING) throw new IllegalStateException("完成记录不能仍为 RUNNING");
            if ((completed.status() == RunStatus.FILLED) != (execution != null)) {
                throw new IllegalStateException("成交状态与账本更新不一致");
            }
            if (execution != null) {
                if (execution.cashUsdt().signum() < 0 || execution.positions().stream().anyMatch(p -> p.quantity().signum() <= 0
                        || p.costBasisUsdt().signum() < 0)) throw new IllegalStateException("账本不能包含负现金或无效持仓");
                jdbc.update("UPDATE trading_paper_account SET cash_usdt=?,fees_usdt=?,realized_pnl_usdt=? WHERE account_id=?",
                        execution.cashUsdt(), execution.feesUsdt(), execution.realizedPnlUsdt(), accountId);
                jdbc.update("DELETE FROM trading_paper_position WHERE account_id=?", accountId);
                jdbc.update("DELETE FROM trading_paper_position_plan WHERE account_id=?", accountId);
                for (Position position : execution.positions()) {
                    jdbc.update("INSERT INTO trading_paper_position (account_id,symbol,quantity,cost_basis_usdt) VALUES (?,?,?,?)",
                            accountId, position.symbol(), position.quantity(), position.costBasisUsdt());
                    if (position.plan() != null) jdbc.update("""
                            INSERT INTO trading_paper_position_plan (account_id,symbol,plan_json) VALUES (?,?,?)
                            """, accountId, position.symbol(), json(position.plan()));
                }
            }
            jdbc.update("UPDATE trading_paper_account SET active_run_id=NULL,version=version+1 WHERE account_id=?", accountId);
            jdbc.update("""
                    UPDATE trading_paper_run SET status=?,decision_json=?,fill_json=?,after_json=?,error_message=?,
                    debug_trace_json=?,completed_at_epoch_ms=? WHERE run_id=?
                    """, completed.status().name(), json(completed.decision()), json(execution == null ? null : execution.fill()),
                    json(completed.after()), completed.error(), JSON.toJSONString(completed.debugTrace()), now, runId);
            observe(accountId, runId + ":after", account.version() * 2 + 1, completed.after());
            return findRun(accountId, "run_id", runId);
        });
    }

    private Account loadAccount(long ownerId, String id, boolean lock) {
        List<Account> rows = jdbc.query("SELECT * FROM trading_paper_account WHERE account_id=? AND owner_id=?"
                        + (lock ? " FOR UPDATE" : ""), (rs, number) -> new Account(rs.getString("account_id"),
                        rs.getLong("owner_id"), rs.getString("name"), rs.getBigDecimal("initial_cash_usdt"),
                        rs.getBigDecimal("cash_usdt"), rs.getBigDecimal("fees_usdt"), rs.getBigDecimal("realized_pnl_usdt"),
                        rs.getLong("version"), rs.getString("active_run_id"),
                        JSON.parseObject(rs.getString("rules_json"), Rules.class), List.of(), rs.getLong("created_at_epoch_ms")),
                id, ownerId);
        if (rows.isEmpty()) throw new PaperTradingException(404, "模拟账户不存在");
        Account row = rows.getFirst();
        List<Position> positions = jdbc.query("""
                        SELECT p.*,m.plan_json FROM trading_paper_position p LEFT JOIN trading_paper_position_plan m
                        ON p.account_id=m.account_id AND p.symbol=m.symbol WHERE p.account_id=? ORDER BY p.symbol
                        """, (rs, number) -> new Position(rs.getString("symbol"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("cost_basis_usdt"), parse(rs, "plan_json", PositionPlan.class)), id);
        return new Account(row.accountId(), row.ownerId(), row.name(), row.initialCashUsdt(), row.cashUsdt(), row.feesUsdt(),
                row.realizedPnlUsdt(), row.version(), row.activeRunId(), row.rules(), List.copyOf(positions), row.createdAtEpochMillis());
    }

    /** 调用方须已开启事务；供自动化设置和日报与成交共用同一把账户行锁。 */
    Account lockAccount(long ownerId, String accountId) { return loadAccount(ownerId, accountId, true); }

    public Position updateProtection(long ownerId, String accountId, String symbol,
                                     java.math.BigDecimal stop, java.math.BigDecimal target) {
        return transaction.execute(status -> {
            Account account = loadAccount(ownerId, accountId, true);
            if (account.activeRunId() != null) throw new PaperTradingException(409, "账户有运行正在执行，稍后再修改保护计划");
            Position position = account.positions().stream().filter(p -> p.symbol().equals(symbol)).findFirst()
                    .orElseThrow(() -> new PaperTradingException(404, "该账户没有此持仓"));
            PositionPlan old = position.plan();
            PositionPlan plan = new PositionPlan(old == null ? null : old.openedAtEpochMillis(),
                    old == null ? null : old.entryThesis(), stop, target);
            jdbc.update("DELETE FROM trading_paper_position_plan WHERE account_id=? AND symbol=?", accountId, symbol);
            jdbc.update("INSERT INTO trading_paper_position_plan (account_id,symbol,plan_json) VALUES (?,?,?)", accountId, symbol, json(plan));
            jdbc.update("UPDATE trading_paper_account SET version=version+1 WHERE account_id=?", accountId);
            return new Position(symbol, position.quantity(), position.costBasisUsdt(), plan);
        });
    }

    private void observe(String accountId, String sampleId, long sequence, Snapshot snapshot) {
        if (snapshot == null || !snapshot.valuationComplete() || snapshot.equityUsdt() == null) return;
        jdbc.update("""
                INSERT INTO trading_paper_equity_sample
                (sample_id,account_id,sample_sequence,as_of_epoch_ms,equity_usdt,btc_price_usdt) VALUES (?,?,?,?,?,?)
                """, sampleId, accountId, sequence, snapshot.asOfEpochMillis(), snapshot.equityUsdt(), snapshot.benchmarkBtcPriceUsdt());
    }

    List<Run> dailyRuns(String accountId, long start, long end) {
        return jdbc.query("""
                SELECT * FROM trading_paper_run WHERE account_id=? AND
                ((status='RUNNING' AND started_at_epoch_ms>=? AND started_at_epoch_ms<?)
                 OR (status<>'RUNNING' AND completed_at_epoch_ms>=? AND completed_at_epoch_ms<?))
                ORDER BY COALESCE(completed_at_epoch_ms,started_at_epoch_ms),run_id
                """, RUN_MAPPER, accountId, start, end, start, end);
    }

    private void expire(Account account, Run run, long now) {
        jdbc.update("""
                UPDATE trading_paper_run SET status='ERROR',error_message=?,after_json=?,completed_at_epoch_ms=?
                WHERE run_id=? AND status='RUNNING'
                """, "上次运行超时，已释放账户；该运行不会再提交成交",
                JSON.toJSONString(PaperBroker.snapshot(account, null, Map.of(), now)), now, run.runId());
        jdbc.update("UPDATE trading_paper_account SET active_run_id=NULL,version=version+1 WHERE account_id=?", account.accountId());
    }

    private Run findRun(String accountId, String column, String value) {
        if (!"run_id".equals(column)) throw new IllegalArgumentException("不支持的账本查询列");
        List<Run> rows = jdbc.query("SELECT * FROM trading_paper_run WHERE account_id=? AND run_id=?", RUN_MAPPER, accountId, value);
        if (rows.isEmpty()) throw new PaperTradingException(404, "运行记录不存在");
        return rows.getFirst();
    }

    private static final RowMapper<Run> RUN_MAPPER = (rs, number) -> new Run(rs.getString("run_id"), rs.getString("account_id"),
            rs.getString("idempotency_key"), RunStatus.valueOf(rs.getString("status")), parse(rs, "rules_json", Rules.class),
            parse(rs, "decision_json", TradeDecision.class), parse(rs, "fill_json", Fill.class), parse(rs, "before_json", Snapshot.class),
            parse(rs, "after_json", Snapshot.class), rs.getString("error_message"),
            JSON.parseObject(rs.getString("debug_trace_json"), new TypeReference<List<String>>() {}),
            rs.getLong("started_at_epoch_ms"), rs.getObject("completed_at_epoch_ms", Long.class));

    private static <T> T parse(ResultSet rs, String field, Class<T> type) throws SQLException {
        String json = rs.getString(field);
        return json == null ? null : JSON.parseObject(json, type);
    }

    private static String json(Object value) { return value == null ? null : JSON.toJSONString(value); }
    private static String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
}
