package com.hollow.build.scheduler;

import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.service.OcrTaskStateService;
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
     * 定时扫描 Redis 中的 OCR 任务，将超时未消费的 PENDING 任务标记为 FAILED。
     */
    @Scheduled(fixedDelayString = "${com.hollow.ocr.pending-timeout-scan-interval-millis:60000}")
    public void markTimedOutPendingTasks() {
        for (String taskId : ocrTaskStateService.findAllTaskIds()) {
            OcrTaskDto task = ocrTaskStateService.getTask(taskId);
            if (!ocrTaskStateService.isPendingTimedOut(task)) {
                continue;
            }

            ocrTaskStateService.markPendingTimeout(taskId);
            log.warn("OCR 任务排队超时，已自动标记失败: taskId={}", taskId);
        }
    }
}
