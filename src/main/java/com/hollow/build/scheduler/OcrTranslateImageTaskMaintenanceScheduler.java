package com.hollow.build.scheduler;

import com.hollow.build.dto.OcrTranslateImageTaskDto;
import com.hollow.build.service.OcrTranslateImageTaskStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OCR 图片翻译标注任务维护调度器，负责清理长时间停留在 PENDING 状态的异常任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTranslateImageTaskMaintenanceScheduler {

    private final OcrTranslateImageTaskStateService taskStateService;

    @Scheduled(fixedDelayString = "${com.hollow.ocr.pending-timeout-scan-interval-millis:60000}")
    public void markPendingTasksTimeout() {
        for (String taskId : taskStateService.findAllTaskIds()) {
            OcrTranslateImageTaskDto task = taskStateService.getTask(taskId);
            if (task == null) {
                taskStateService.removeFromActiveSet(taskId);
                log.info("OCR 翻译标注任务记录已过期，已从活跃集合中清理: taskId={}", taskId);
                continue;
            }

            if (!taskStateService.isPendingTimedOut(task)) {
                continue;
            }

            taskStateService.markPendingTimeout(taskId);
            log.warn("OCR 翻译标注任务排队超时，已自动标记失败: taskId={}", taskId);
        }
    }
}
