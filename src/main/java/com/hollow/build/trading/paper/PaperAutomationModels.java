package com.hollow.build.trading.paper;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static com.hollow.build.trading.paper.PaperModels.*;

public final class PaperAutomationModels {
    private PaperAutomationModels() {}
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public record Automation(String accountId, boolean enabled, String dailyRunTime, String zone,
                             Long nextRunAtEpochMillis, String lastRunDate, String lastRunId,
                             long version, boolean schedulerEnabled) {}

    public record DueAccount(long ownerId, String accountId, String dailyRunTime, long version) {}

    public record DailyActivity(String runId, RunStatus status, TradeDecision decision, Fill fill,
                                String error, Long completedAtEpochMillis) {}

    public record DailyReport(String reportId, String accountId, String reportDate, String zone,
                              long generatedAtEpochMillis, Map<String, Integer> statusCounts,
                              int tradeCount, BigDecimal dailyFeesUsdt, BigDecimal dailyRealizedPnlUsdt,
                              Snapshot portfolio, List<DailyActivity> activities, String summaryText,
                              PaperPerformanceService.Performance performance) {
        public DailyReport(String reportId, String accountId, String reportDate, String zone, long generatedAtEpochMillis,
                           Map<String, Integer> statusCounts, int tradeCount, BigDecimal dailyFeesUsdt,
                           BigDecimal dailyRealizedPnlUsdt, Snapshot portfolio, List<DailyActivity> activities, String summaryText) {
            this(reportId, accountId, reportDate, zone, generatedAtEpochMillis, statusCounts, tradeCount,
                    dailyFeesUsdt, dailyRealizedPnlUsdt, portfolio, activities, summaryText, null);
        }
    }

    public record AutomationResult(Run run, DailyReport report) {}
}
