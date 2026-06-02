package com.hollow.build.ocr.scheduler;

import com.hollow.build.ocr.dto.OcrTaskDto;
import com.hollow.build.ocr.service.OcrTaskStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OCR 任务维护调度器，负责清理长时间停留在 PENDING 状态的异常任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTaskMaintenanceScheduler {

    private final OcrTaskStateService ocrTaskStateService;

    /**
     * 定时扫描活跃任务集合（ocr:active-tasks），将超时未消费的 PENDING 任务标记为 FAILED。
     * 同时清理任务记录已被 Redis TTL 回收但仍残留在集合中的过期条目。
     */
    @Scheduled(fixedDelayString = "${com.hollow.ocr.pending-timeout-scan-interval-millis:60000}")
    public void markTimedOutPendingTasks() {
        for (String taskId : ocrTaskStateService.findAllTaskIds()) {
            OcrTaskDto task = ocrTaskStateService.getTask(taskId);

            // 自愈：如果任务记录已过期（被 Redis TTL 清理），则从活跃集合中移除
            if (task == null) {
                ocrTaskStateService.removeFromActiveSet(taskId);
                log.info("OCR 任务记录已过期，已从活跃集合中清理: taskId={}", taskId);
                continue;
            }

            if (!ocrTaskStateService.isPendingTimedOut(task)) {
                continue;
            }

            ocrTaskStateService.markPendingTimeout(taskId);
            log.warn("OCR 任务排队超时，已自动标记失败: taskId={}", taskId);
        }
    }
}
