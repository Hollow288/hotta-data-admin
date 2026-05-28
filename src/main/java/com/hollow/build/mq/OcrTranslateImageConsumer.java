package com.hollow.build.mq;

import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.config.RabbitMQConfig;
import com.hollow.build.dto.OcrTranslateImageTaskDto;
import com.hollow.build.dto.OcrTranslatedImageResult;
import com.hollow.build.service.OcrTranslateImageProcessor;
import com.hollow.build.service.OcrTranslateImageTaskStateService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * OCR 图片翻译标注任务消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTranslateImageConsumer {

    private static final DateTimeFormatter OBJECT_MONTH_PATH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM");

    private final RabbitTemplate rabbitTemplate;
    private final OcrTranslateImageTaskStateService taskStateService;
    private final OcrTranslateImageProcessor processor;
    private final OcrConfigurationProperties ocrConfig;
    private final MinioUtil minioUtil;

    @RabbitListener(queues = RabbitMQConfig.OCR_TRANSLATE_QUEUE)
    public void handleTranslateImageTask(Map<String, Object> message) {
        String taskId = stringValue(message.get("taskId"));
        String bucketName = stringValue(message.get("bucketName"));
        String objectName = stringValue(message.get("objectName"));
        String fileName = stringValue(message.get("fileName"));
        String contentType = stringValue(message.get("contentType"));
        String targetLanguage = stringValue(message.get("targetLanguage"));
        Double minConfidence = doubleValue(message.get("minConfidence"));
        int retryCount = intValue(message.get("retryCount"));

        if (taskId == null || taskId.isBlank() || bucketName == null || bucketName.isBlank()
                || objectName == null || objectName.isBlank()) {
            log.error("收到结构异常的 OCR 翻译标注消息，已投递到死信队列: {}", message);
            publishToDeadLetter(message, "OCR 翻译标注消息缺少必要字段");
            return;
        }

        OcrTranslateImageTaskDto current = taskStateService.getTask(taskId);
        if (current == null) {
            log.warn("OCR 翻译标注任务记录已不存在（可能已过期），丢弃消息: taskId={}", taskId);
            return;
        }
        String currentStatus = current.getStatus();
        if ("SUCCESS".equals(currentStatus) || "FAILED".equals(currentStatus)) {
            log.warn("OCR 翻译标注任务已处于终态 {}，丢弃此消息: taskId={}", currentStatus, taskId);
            return;
        }

        log.info("开始处理 OCR 翻译标注任务: taskId={}, fileName={}, targetLanguage={}, retryCount={}",
                taskId, fileName, targetLanguage, retryCount);
        taskStateService.updateStatus(taskId, "PROCESSING", null, retryCount);

        try {
            byte[] imageBytes;
            try (InputStream is = minioUtil.getObject(bucketName, objectName)) {
                imageBytes = is.readAllBytes();
            }

            OcrTranslatedImageResult result = processor.process(
                    imageBytes,
                    fileName,
                    contentType,
                    targetLanguage,
                    minConfidence
            );

            String resultBucketName = ocrConfig.getMinioBucket();
            String resultObjectName = "ocr-translate/"
                    + LocalDate.now().format(OBJECT_MONTH_PATH_FORMATTER)
                    + "/" + taskId + "/result.png";
            minioUtil.ensureBucketExists(resultBucketName);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(result.imageBytes())) {
                minioUtil.uploadChunk(resultBucketName, resultObjectName, inputStream, "image/png");
            }

            String resultImageUrl = minioUtil.getPreviewFileUrl(
                    resultBucketName,
                    resultObjectName,
                    presignedUrlExpirySeconds()
            );
            taskStateService.updateSuccess(
                    taskId,
                    result.itemCount(),
                    resultImageUrl,
                    resultBucketName,
                    resultObjectName,
                    retryCount
            );
            log.info("OCR 翻译标注任务完成: taskId={}, items={}, resultObject={}",
                    taskId, result.itemCount(), resultObjectName);
        } catch (Exception e) {
            handleFailure(message, taskId, retryCount, e);
        }
    }

    private int presignedUrlExpirySeconds() {
        long ttl = ocrConfig.getResultTtl();
        return (int) Math.min(7L * 24 * 60 * 60, Math.max(60L, ttl));
    }

    private void handleFailure(Map<String, Object> message, String taskId, int retryCount, Exception e) {
        String errorMsg = safeMessage(e);
        if (retryCount < ocrConfig.getMaxRetryCount()) {
            int nextRetryCount = retryCount + 1;
            Map<String, Object> retryMessage = new HashMap<>(message);
            retryMessage.put("retryCount", nextRetryCount);
            retryMessage.put("lastError", errorMsg);

            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.OCR_TRANSLATE_RETRY_EXCHANGE,
                        RabbitMQConfig.OCR_TRANSLATE_RETRY_ROUTING_KEY,
                        retryMessage
                );
                taskStateService.markPendingForRetry(
                        taskId,
                        nextRetryCount,
                        "OCR 翻译标注处理失败，已进入第 " + nextRetryCount + " 次重试队列"
                );
                log.warn("OCR 翻译标注任务处理失败，已进入重试队列: taskId={}, retryCount={}, error={}",
                        taskId, nextRetryCount, errorMsg);
                return;
            } catch (Exception retryPublishException) {
                errorMsg = "OCR 翻译标注重试入队失败: " + safeMessage(retryPublishException);
                log.error("OCR 翻译标注任务重试入队失败: taskId={}", taskId, retryPublishException);
            }
        }

        taskStateService.updateStatus(taskId, "FAILED", errorMsg, retryCount);
        publishToDeadLetter(message, errorMsg);
        log.error("OCR 翻译标注任务最终失败: taskId={}, retryCount={}, error={}",
                taskId, retryCount, errorMsg, e);
    }

    private void publishToDeadLetter(Map<String, Object> originalMessage, String errorMsg) {
        try {
            Map<String, Object> deadMessage = new HashMap<>(originalMessage);
            deadMessage.put("lastError", errorMsg);
            deadMessage.put("failedAt", System.currentTimeMillis());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OCR_TRANSLATE_DEAD_EXCHANGE,
                    RabbitMQConfig.OCR_TRANSLATE_DEAD_ROUTING_KEY,
                    deadMessage
            );
        } catch (Exception deadLetterException) {
            log.error("OCR 翻译标注死信投递失败: {}", errorMsg, deadLetterException);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "未知错误";
        }
        return exception.getMessage();
    }
}
