package com.hollow.build.trading.paper;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hollow.build.trading.paper.PaperAutomationModels.*;
import static com.hollow.build.trading.paper.PaperModels.*;

/** 仅根据已经保存的事实生成日报，不额外调用模型，不把缺失估值当作零。 */
@Service
public class PaperDailyReportService {
    private final JdbcTemplate jdbc;
    private final PaperLedger ledger;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final PaperPerformanceService performance;

    @Autowired
    public PaperDailyReportService(JdbcTemplate jdbc, PaperLedger ledger, PlatformTransactionManager manager,
                                   PaperPerformanceService performance) {
        this(jdbc, ledger, manager, Clock.systemUTC(), performance);
    }

    public PaperDailyReportService(JdbcTemplate jdbc, PaperLedger ledger, PlatformTransactionManager manager) {
        this(jdbc, ledger, manager, Clock.systemUTC(), new PaperPerformanceService(jdbc));
    }

    PaperDailyReportService(JdbcTemplate jdbc, PaperLedger ledger, PlatformTransactionManager manager, Clock clock) {
        this(jdbc, ledger, manager, clock, new PaperPerformanceService(jdbc));
    }

    private PaperDailyReportService(JdbcTemplate jdbc, PaperLedger ledger, PlatformTransactionManager manager,
                                    Clock clock, PaperPerformanceService performance) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
        this.performance = performance;
    }

    public DailyReport generateToday(long ownerId, String accountId) {
        return transaction.execute(status -> {
            Account account = ledger.lockAccount(ownerId, accountId);
            long now = clock.millis();
            LocalDate date = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
            long start = date.atStartOfDay(ZONE).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
            List<Run> runs = ledger.dailyRuns(accountId, start, end);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (RunStatus value : RunStatus.values()) counts.put(value.name(), 0);
            BigDecimal fees = BigDecimal.ZERO;
            BigDecimal realized = BigDecimal.ZERO;
            Snapshot portfolio = PaperBroker.snapshot(account, null, Map.of(), now);
            for (Run run : runs) {
                counts.merge(run.status().name(), 1, Integer::sum);
                if (run.fill() != null) {
                    fees = fees.add(run.fill().feeUsdt());
                    realized = realized.add(run.fill().realizedPnlUsdt());
                }
                if (run.after() != null) {
                    // 当前资金/持仓以已锁定账户为准；同毫秒完成的多轮不能靠 UUID 顺序覆盖现金。
                    portfolio = PaperBroker.snapshot(account, null, run.after().markPricesUsdt(), run.after().asOfEpochMillis());
                }
            }
            List<DailyActivity> activities = runs.stream().map(run -> new DailyActivity(run.runId(), run.status(),
                    run.decision(), run.fill(), run.error(), run.completedAtEpochMillis())).toList();
            List<String> old = jdbc.queryForList("SELECT report_id FROM trading_paper_daily_report WHERE account_id=? AND report_date=?",
                    String.class, accountId, date.toString());
            String id = old.isEmpty() ? UUID.randomUUID().toString().replace("-", "") : old.getFirst();
            var metrics = performance.calculate(accountId);
            DailyReport report = new DailyReport(id, accountId, date.toString(), ZONE.getId(), now, Map.copyOf(counts),
                    counts.get(RunStatus.FILLED.name()), fees, realized, portfolio, activities,
                    summary(date, now, counts, fees, realized, portfolio, activities) + performanceText(metrics), metrics);
            String json = JSON.toJSONString(report);
            if (old.isEmpty()) {
                jdbc.update("""
                        INSERT INTO trading_paper_daily_report (report_id,account_id,report_date,report_json,generated_at_epoch_ms)
                        VALUES (?,?,?,?,?)
                        """, id, accountId, date.toString(), json, now);
            } else {
                jdbc.update("UPDATE trading_paper_daily_report SET report_json=?,generated_at_epoch_ms=? WHERE report_id=?", json, now, id);
            }
            return report;
        });
    }

    public List<DailyReport> list(long ownerId, String accountId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit 必须在 1～100 之间");
        ledger.account(ownerId, accountId);
        return jdbc.query("SELECT report_json FROM trading_paper_daily_report WHERE account_id=? ORDER BY report_date DESC LIMIT ?",
                (rs, row) -> JSON.parseObject(rs.getString(1), DailyReport.class), accountId, limit);
    }

    public DailyReport get(long ownerId, String accountId, String date) {
        ledger.account(ownerId, accountId);
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("日报日期格式应为 yyyy-MM-dd");
        try { LocalDate.parse(date); } catch (java.time.DateTimeException e) { throw new IllegalArgumentException("日报日期无效"); }
        List<DailyReport> reports = jdbc.query("SELECT report_json FROM trading_paper_daily_report WHERE account_id=? AND report_date=?",
                (rs, row) -> JSON.parseObject(rs.getString(1), DailyReport.class), accountId, date);
        if (reports.isEmpty()) throw new PaperTradingException(404, "该日期的日报尚未生成");
        return reports.getFirst();
    }

    private static String summary(LocalDate date, long now, Map<String, Integer> counts, BigDecimal fees,
                                  BigDecimal realized, Snapshot portfolio, List<DailyActivity> activities) {
        StringBuilder text = new StringBuilder("模拟投资日报 · ").append(date).append("（北京时间）\n")
                .append("生成时间：").append(time(now)).append("\n")
                .append("今日成交 ").append(counts.get("FILLED")).append(" 笔，观望 ").append(counts.get("HOLD"))
                .append(" 次，拒绝 ").append(counts.get("REJECTED")).append(" 次，失败 ").append(counts.get("ERROR"))
                .append(" 次，执行中 ").append(counts.get("RUNNING")).append(" 次。\n")
                .append("现金：").append(number(portfolio.cashUsdt())).append(" 虚拟 USDT；今日手续费：")
                .append(number(fees)).append(" USDT；今日已实现盈亏：").append(number(realized)).append(" USDT。\n");
        if (portfolio.valuationComplete()) {
            text.append("资产估值：").append(number(portfolio.equityUsdt())).append(" USDT；累计盈亏：")
                    .append(number(portfolio.totalPnlUsdt())).append(" USDT。\n");
        } else {
            text.append("资产估值及累计盈亏暂缺：部分持仓缺少有效行情。\n");
        }
        text.append("账户快照时间：").append(time(portfolio.asOfEpochMillis())).append("，非持续刷新的实时价格。\n");
        if (portfolio.positions().isEmpty()) text.append("持仓：空仓。\n");
        for (Position position : portfolio.positions()) {
            text.append("持仓：").append(position.symbol()).append(" 数量 ").append(number(position.quantity()))
                    .append("，成本（含买入手续费）").append(number(position.costBasisUsdt())).append(" USDT。\n");
        }
        if (activities.isEmpty()) text.append("今天尚无运行记录。\n");
        activities.stream().skip(Math.max(0, activities.size() - 5)).forEach(activity -> {
            text.append("- ").append(activity.status());
            if (activity.decision() != null) text.append(" / ").append(activity.decision().action())
                    .append("：").append(activity.decision().reason());
            if (activity.error() != null) text.append("；原因：").append(activity.error());
            text.append('\n');
        });
        return text.toString().stripTrailing();
    }

    private static String performanceText(PaperPerformanceService.Performance metrics) {
        StringBuilder text = new StringBuilder("\n绩效（自可用采样开始）：完整估值 ").append(metrics.equitySampleCount()).append(" 点。");
        if (metrics.sampledMaxDrawdownPercent() != null) text.append("采样最大回撤 ").append(number(metrics.sampledMaxDrawdownPercent())).append("%。");
        if (metrics.btcBuyHoldReturnPercent() == null) text.append("BTC 基准暂缺。");
        else text.append("同期策略收益 ").append(number(metrics.strategyReturnPercent())).append("%，BTC 持有收益 ")
                .append(number(metrics.btcBuyHoldReturnPercent())).append("%，超额 ").append(number(metrics.excessReturnPercentagePoints()))
                .append(" 个百分点；区间 ").append(time(metrics.benchmarkStartEpochMillis())).append(" 至 ")
                .append(time(metrics.benchmarkEndEpochMillis())).append("（BTC 基准未扣费用）。");
        if (metrics.sellFillWinRatePercent() != null) text.append("卖出成交胜率 ").append(number(metrics.sellFillWinRatePercent())).append("%。");
        text.append("回撤仅来自采样，胜率按卖出成交笔计算，详见 performance.methodology。");
        return text.toString();
    }

    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String time(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
