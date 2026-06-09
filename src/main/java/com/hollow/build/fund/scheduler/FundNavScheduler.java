package com.hollow.build.fund.scheduler;

import com.hollow.build.fund.FundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金净值定时拉取任务。
 * <p>
 * 每晚拉一次 + 次日凌晨兜底再拉一次（覆盖 QDII 净值 T+1 延迟）。
 * 两次任务都做增量拉取并自动补缺口，靠唯一键去重，重复拉取不会产生脏数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundNavScheduler {

    private final FundService fundService;

    /**
     * 每晚拉取最新净值（默认 23:30）。
     */
    @Scheduled(cron = "${com.hollow.fund.nav-sync-cron:0 30 23 * * ?}")
    public void syncNightly() {
        log.info("[基金定时] 每晚净值拉取开始");
        fundService.syncAllLatest();
    }

    /**
     * 次日凌晨兜底再拉一次（默认 05:30），提高 QDII 当天净值的及时性。
     */
    @Scheduled(cron = "${com.hollow.fund.nav-backup-cron:0 30 5 * * ?}")
    public void syncBackup() {
        log.info("[基金定时] 凌晨兜底净值拉取开始");
        fundService.syncAllLatest();
    }
}
