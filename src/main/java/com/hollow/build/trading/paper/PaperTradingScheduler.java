package com.hollow.build.trading.paper;

import com.hollow.build.trading.config.TradingProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 私有调度线程不会成为 Spring 其他 @Scheduled 任务的默认线程池。 */
@Slf4j
@Component
public class PaperTradingScheduler {
    private final PaperAutomationStore store;
    private final PaperAutomationService automation;
    private final TradingProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public PaperTradingScheduler(PaperAutomationStore store, PaperAutomationService automation, TradingProperties properties) {
        this.store = store;
        this.automation = automation;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!properties.getPaper().isSchedulerEnabled()) {
            log.info("模拟交易定时任务未开启：设置 com.hollow.trading.paper.scheduler-enabled=true 后生效");
            return;
        }
        long interval = properties.getPaper().getSchedulerPollInterval().toMillis();
        int batch = properties.getPaper().getSchedulerBatchSize();
        if (interval < 1000 || interval > 3_600_000 || batch < 1 || batch > 100) {
            throw new IllegalArgumentException("模拟调度轮询间隔应在 1 秒～1 小时，批次大小应在 1～100");
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "paper-trading-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);
        log.info("模拟交易定时任务开启：poll={}ms，账户需单独开启，时区 Asia/Shanghai", interval);
    }

    public void tick() {
        if (!properties.getPaper().isSchedulerEnabled() || !running.compareAndSet(false, true)) return;
        try {
            var due = store.due(Clock.systemUTC().millis(), properties.getPaper().getSchedulerBatchSize());
            for (var account : due) {
                if (Thread.currentThread().isInterrupted()) break;
                try {
                    automation.runDue(account);
                } catch (PaperTradingException error) {
                    log.warn("模拟自动实验暂未完成：account={}，code={}，reason={}", account.accountId(), error.getCode(), error.getMessage());
                } catch (Exception error) {
                    log.error("模拟自动实验失败，下轮重试：account={}", account.accountId(), error);
                }
            }
        } catch (Exception error) {
            log.error("模拟调度扫描失败，请检查连接并执行 docs/sql/trading_paper.sql 补建自动化/日报表", error);
        } finally {
            running.set(false);
        }
    }

    @PreDestroy
    public void stop() { if (executor != null) executor.shutdownNow(); }
}
