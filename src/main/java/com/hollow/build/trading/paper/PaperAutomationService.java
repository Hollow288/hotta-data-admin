package com.hollow.build.trading.paper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static com.hollow.build.trading.paper.PaperAutomationModels.*;
import static com.hollow.build.trading.paper.PaperModels.RunStatus;

/** 调度入口直接复用普通编排服务，不通过聊天 Router。 */
@Service
public class PaperAutomationService {
    private final PaperAutomationStore store;
    private final PaperTradingService trading;
    private final PaperDailyReportService reports;
    private final Clock clock;

    @Autowired
    public PaperAutomationService(PaperAutomationStore store, PaperTradingService trading, PaperDailyReportService reports) {
        this(store, trading, reports, Clock.systemUTC());
    }

    PaperAutomationService(PaperAutomationStore store, PaperTradingService trading, PaperDailyReportService reports, Clock clock) {
        this.store = store;
        this.trading = trading;
        this.reports = reports;
        this.clock = clock;
    }

    public Automation get(long ownerId, String accountId) { return store.get(ownerId, accountId); }

    public Automation configure(long ownerId, String accountId, Boolean enabled, String dailyRunTime) {
        if (enabled == null) throw new IllegalArgumentException("enabled 必填");
        return store.configure(ownerId, accountId, enabled, dailyRunTime, clock.millis());
    }

    /** 用于立即验收自动流程；同一天与后台定时运行共享同一个幂等 key。 */
    public AutomationResult runToday(long ownerId, String accountId, String ip) {
        Automation settings = store.get(ownerId, accountId);
        if (!settings.enabled()) throw new PaperTradingException(409, "该账户尚未开启自动实验，请先配置 automation.enabled=true");
        return run(settings, ownerId, ip);
    }

    void runDue(DueAccount due) {
        Automation current = store.get(due.ownerId(), due.accountId());
        if (!current.enabled() || current.version() != due.version()) return;
        long now = clock.millis();
        if (current.nextRunAtEpochMillis() == null || current.nextRunAtEpochMillis() > now) return;
        var localNow = Instant.ofEpochMilli(now).atZone(ZONE);
        if (localNow.toLocalTime().isBefore(PaperAutomationStore.parseTime(current.dailyRunTime()))) {
            store.deferUntilToday(due, localNow.toLocalDate(), now);
            return;
        }
        run(current, due.ownerId(), "scheduler");
    }

    private AutomationResult run(Automation settings, long ownerId, String ip) {
        LocalDate day = Instant.ofEpochMilli(clock.millis()).atZone(ZONE).toLocalDate();
        var run = trading.runOnce(ownerId, settings.accountId(), "auto:" + day, ip);
        if (run.status() == RunStatus.RUNNING) return new AutomationResult(run, null);
        DailyReport report = reports.generateToday(ownerId, settings.accountId());
        // 日报写入失败时不推进进度；下次重试只读取原成交，再生成日报，不再次调用模型或成交。
        store.advance(new DueAccount(ownerId, settings.accountId(), settings.dailyRunTime(), settings.version()),
                day, run.runId(), clock.millis());
        return new AutomationResult(run, report);
    }
}
