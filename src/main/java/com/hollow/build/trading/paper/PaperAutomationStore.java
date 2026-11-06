package com.hollow.build.trading.paper;

import com.hollow.build.trading.config.TradingProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.hollow.build.trading.paper.PaperAutomationModels.*;

/** 保存调度进度；同账户共用账本行锁，网络/模型执行期间不持有事务。 */
@Repository
public class PaperAutomationStore {
    private final JdbcTemplate jdbc;
    private final PaperLedger ledger;
    private final TransactionTemplate transaction;
    private final TradingProperties properties;

    public PaperAutomationStore(JdbcTemplate jdbc, PaperLedger ledger, PlatformTransactionManager manager,
                                TradingProperties properties) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.transaction = new TransactionTemplate(manager);
        this.properties = properties;
    }

    public Automation get(long ownerId, String accountId) {
        ledger.account(ownerId, accountId);
        return load(accountId);
    }

    public Automation configure(long ownerId, String accountId, boolean enabled, String time, long now) {
        LocalTime runTime = parseTime(time);
        return transaction.execute(status -> {
            ledger.lockAccount(ownerId, accountId);
            Automation old = load(accountId);
            LocalDate today = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
            LocalDate nextDay = today.toString().equals(old.lastRunDate()) ? today.plusDays(1) : today;
            Long next = enabled ? at(nextDay, runTime) : null;
            int updated = jdbc.update("""
                    UPDATE trading_paper_automation SET enabled=?,daily_run_time=?,next_run_at_epoch_ms=?,
                    version=version+1,updated_at_epoch_ms=? WHERE account_id=?
                    """, enabled, time, next, now, accountId);
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO trading_paper_automation
                        (account_id,enabled,daily_run_time,next_run_at_epoch_ms,version,updated_at_epoch_ms)
                        VALUES (?,?,?,?,1,?)
                        """, accountId, enabled, time, next, now);
            }
            return load(accountId);
        });
    }

    public List<DueAccount> due(long now, int limit) {
        return jdbc.query("""
                SELECT a.owner_id,s.account_id,s.daily_run_time,s.version
                FROM trading_paper_automation s JOIN trading_paper_account a ON a.account_id=s.account_id
                WHERE s.enabled=TRUE AND s.next_run_at_epoch_ms<=?
                ORDER BY s.next_run_at_epoch_ms,s.account_id LIMIT ?
                """, (rs, row) -> new DueAccount(rs.getLong("owner_id"), rs.getString("account_id"),
                rs.getString("daily_run_time"), rs.getLong("version")), now, limit);
    }

    public void advance(DueAccount due, LocalDate runDay, String runId, long now) {
        jdbc.update("""
                UPDATE trading_paper_automation SET last_run_date=?,last_run_id=?,next_run_at_epoch_ms=?,
                version=version+1,updated_at_epoch_ms=? WHERE account_id=? AND enabled=TRUE AND version=?
                """, runDay.toString(), runId, at(runDay.plusDays(1), parseTime(due.dailyRunTime())),
                now, due.accountId(), due.version());
    }

    /** 停机期间只补当天已到点的一次；今天未到点时把旧进度顺延到今天。 */
    public void deferUntilToday(DueAccount due, LocalDate today, long now) {
        jdbc.update("""
                UPDATE trading_paper_automation SET next_run_at_epoch_ms=?,version=version+1,updated_at_epoch_ms=?
                WHERE account_id=? AND enabled=TRUE AND version=?
                """, at(today, parseTime(due.dailyRunTime())), now, due.accountId(), due.version());
    }

    private Automation load(String accountId) {
        List<Automation> rows = jdbc.query("SELECT * FROM trading_paper_automation WHERE account_id=?",
                (rs, row) -> new Automation(accountId, rs.getBoolean("enabled"), rs.getString("daily_run_time"),
                        ZONE.getId(), rs.getObject("next_run_at_epoch_ms", Long.class), rs.getString("last_run_date"),
                        rs.getString("last_run_id"), rs.getLong("version"), properties.getPaper().isSchedulerEnabled()), accountId);
        return rows.isEmpty() ? new Automation(accountId, false, "09:00", ZONE.getId(), null, null, null,
                0, properties.getPaper().isSchedulerEnabled()) : rows.getFirst();
    }

    static LocalTime parseTime(String time) {
        if (time == null || !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) {
            throw new IllegalArgumentException("dailyRunTime 必须为北京时间 HH:mm，如 09:00");
        }
        return LocalTime.parse(time);
    }

    static long at(LocalDate date, LocalTime time) { return date.atTime(time).atZone(ZONE).toInstant().toEpochMilli(); }
}
