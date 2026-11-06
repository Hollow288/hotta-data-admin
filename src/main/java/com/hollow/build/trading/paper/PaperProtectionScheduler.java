package com.hollow.build.trading.paper;

import com.hollow.build.trading.config.TradingProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 独立于每日模型线程；即便模型调用耗时，保护性巡检仍能扫描其他账户。 */
@Slf4j
@Component
public class PaperProtectionScheduler {
    private final JdbcTemplate jdbc;
    private final PaperProtectionService protection;
    private final PaperDailyReportService reports;
    private final TradingProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;
    private String cursor = "";

    public PaperProtectionScheduler(JdbcTemplate jdbc, PaperProtectionService protection,
                                    PaperDailyReportService reports, TradingProperties properties) {
        this.jdbc = jdbc;
        this.protection = protection;
        this.reports = reports;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!properties.getPaper().isProtectionEnabled()) return;
        long interval = properties.getPaper().getProtectionPollInterval().toMillis();
        int batch = properties.getPaper().getProtectionBatchSize();
        if (interval < 1000 || interval > 3_600_000 || batch < 1 || batch > 100) {
            throw new IllegalArgumentException("保护巡检间隔应在 1 秒～1 小时，批次大小应在 1～100");
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "paper-protection-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void tick() {
        if (!properties.getPaper().isProtectionEnabled() || !running.compareAndSet(false, true)) return;
        try {
            int batch = properties.getPaper().getProtectionBatchSize();
            var accounts = jdbc.query("""
                    SELECT DISTINCT a.owner_id,a.account_id FROM trading_paper_account a
                    JOIN trading_paper_position_plan p ON a.account_id=p.account_id
                    WHERE a.account_id>? ORDER BY a.account_id LIMIT ?
                    """, (rs, row) -> new Target(rs.getLong("owner_id"), rs.getString("account_id")), cursor, batch);
            for (Target target : accounts) {
                if (Thread.currentThread().isInterrupted()) break;
                cursor = target.accountId();
                try {
                    if (!protection.check(target.ownerId(), target.accountId()).isEmpty()) {
                        reports.generateToday(target.ownerId(), target.accountId());
                    }
                } catch (Exception error) {
                    log.warn("保护性巡检暂未完成：account={}，reason={}", target.accountId(), error.getMessage());
                }
            }
            if (accounts.size() < batch) cursor = "";
        } catch (Exception error) {
            log.error("保护性巡检扫描失败，请检查数据库及 trading_paper.sql 升级表", error);
        } finally { running.set(false); }
    }

    private record Target(long ownerId, String accountId) {}
    @PreDestroy
    public void stop() { if (executor != null) executor.shutdownNow(); }
}
