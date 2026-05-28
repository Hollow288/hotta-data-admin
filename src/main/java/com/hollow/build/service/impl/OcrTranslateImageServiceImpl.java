package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.AiConfigurationProperties;
import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.config.RabbitMQConfig;
import com.hollow.build.dto.OcrTranslateImageTaskDto;
import com.hollow.build.service.OcrTranslateImageService;
import com.hollow.build.service.OcrTranslateImageTaskStateService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OCR 图片翻译标注任务生产者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrTranslateImageServiceImpl implements OcrTranslateImageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic", ".heif"
    );

    private static final DateTimeFormatter OBJECT_MONTH_PATH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM");

    private final RabbitTemplate rabbitTemplate;
    private final OcrTranslateImageTaskStateService taskStateService;
    private final OcrConfigurationProperties ocrConfig;
    private final AiConfigurationProperties aiConfig;
    private final MinioUtil minioUtil;

    @Override
    public ApiResponse<OcrTranslateImageTaskDto> submitTask(MultipartFile file,
                                                            String targetLanguage,
                                                            Double minConfidence) {
        ApiResponse<OcrTranslateImageTaskDto> configurationError = validateConfiguration();
        if (configurationError != null) {
            return configurationError;
        }

        ApiResponse<OcrTranslateImageTaskDto> validationError = validateFile(file);
        if (validationError != null) {
            return validationError;
        }

        Double resolvedMinConfidence = resolveMinConfidence(minConfidence);
        if (minConfidence != null && resolvedMinConfidence == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "minConfidence 必须在 [0, 1] 之间",
                    null
            );
        }
        String resolvedTargetLanguage = StringUtils.defaultIfBlank(targetLanguage, "中文").trim();

        String taskId = null;
        try {
            taskId = UUID.randomUUID().toString();
            String fileName = file.getOriginalFilename();
            String monthPath = LocalDate.now().format(OBJECT_MONTH_PATH_FORMATTER);
            String objectName = "ocr-translate/" + monthPath + "/" + taskId + "/source-"
                    + (fileName != null ? fileName : "image.png");
            String bucketName = ocrConfig.getMinioBucket();
            minioUtil.ensureBucketExists(bucketName);
            try (InputStream inputStream = file.getInputStream()) {
                minioUtil.uploadChunk(bucketName, objectName, inputStream, file.getContentType());
            }

            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("bucketName", bucketName);
            message.put("objectName", objectName);
            message.put("fileName", fileName);
            message.put("contentType", file.getContentType());
            message.put("targetLanguage", resolvedTargetLanguage);
            if (resolvedMinConfidence != null) {
                message.put("minConfidence", resolvedMinConfidence);
            }
            message.put("retryCount", 0);

            OcrTranslateImageTaskDto pending = taskStateService.createPendingTask(
                    taskId,
                    resolvedTargetLanguage,
                    resolvedMinConfidence
            );
            CorrelationData correlationData = new CorrelationData(taskId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OCR_TRANSLATE_EXCHANGE,
                    RabbitMQConfig.OCR_TRANSLATE_ROUTING_KEY,
                    message,
                    correlationData
            );
            waitForPublishConfirm(correlationData);

            log.info("OCR 翻译标注任务已提交: taskId={}, fileName={}, targetLanguage={}",
                    taskId, fileName, resolvedTargetLanguage);
            return ApiResponse.success(pending);
        } catch (Exception e) {
            if (taskId != null) {
                taskStateService.updateStatus(taskId, "FAILED", "提交 OCR 翻译标注任务失败: " + safeMessage(e), 0);
            }
            log.error("提交 OCR 翻译标注任务失败", e);
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "提交 OCR 翻译标注任务失败: " + safeMessage(e),
                    null
            );
        }
    }

    @Override
    public ApiResponse<OcrTranslateImageTaskDto> getResult(String taskId) {
        OcrTranslateImageTaskDto result = taskStateService.getTask(taskId);
        if (result == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "任务不存在或已过期",
                    null
            );
        }

        if (taskStateService.isPendingTimedOut(result)) {
            result = taskStateService.markPendingTimeout(taskId);
        }

        return ApiResponse.success(result);
    }

    private ApiResponse<OcrTranslateImageTaskDto> validateConfiguration() {
        if (ocrConfig.getServiceUrl() == null || ocrConfig.getServiceUrl().isBlank()) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(),
                    "OCR 服务地址未配置",
                    null
            );
        }
        if (StringUtils.isBlank(aiConfig.getTextUri())
                || StringUtils.isBlank(aiConfig.getTextModel())
                || aiConfig.getTextApiKey() == null
                || aiConfig.getTextApiKey().stream().allMatch(StringUtils::isBlank)) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(),
                    "AI 文本翻译配置未完整配置",
                    null
            );
        }
        return null;
    }

    private ApiResponse<OcrTranslateImageTaskDto> validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "请上传非空图片文件",
                    null
            );
        }
        if (file.getSize() > ocrConfig.getMaxFileSizeBytes()) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "图片不能超过 " + formatSize(ocrConfig.getMaxFileSizeBytes()),
                    null
            );
        }
        if (!isAllowedImageType(file)) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "仅支持 JPG/PNG/WEBP/BMP/HEIC/HEIF 图片",
                    null
            );
        }
        return null;
    }

    private boolean isAllowedImageType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String normalizedType = contentType.toLowerCase(Locale.ROOT);
            boolean allowedContentType = ocrConfig.getAllowedContentTypes().stream()
                    .map(type -> type.toLowerCase(Locale.ROOT))
                    .filter(type -> type.startsWith("image/"))
                    .anyMatch(normalizedType::equals);
            if (allowedContentType) {
                return true;
            }
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        return ALLOWED_IMAGE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private Double resolveMinConfidence(Double minConfidence) {
        Double candidate = minConfidence != null ? minConfidence : ocrConfig.getDefaultMinConfidence();
        if (candidate == null) {
            return null;
        }
        if (candidate < 0.0 || candidate > 1.0) {
            return null;
        }
        return candidate;
    }

    private void waitForPublishConfirm(CorrelationData correlationData) throws Exception {
        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(ocrConfig.getSubmitConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ 未确认消息: " + safeMessage(confirm.getReason()));
        }

        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            throw new IllegalStateException("RabbitMQ 路由失败: " + returned.getReplyText());
        }
    }

    private String formatSize(long bytes) {
        return String.format(Locale.ROOT, "%.1fMB", bytes / 1024.0 / 1024.0);
    }

    private String safeMessage(Object value) {
        if (value == null) {
            return "未知错误";
        }
        String text = value.toString();
        return text == null || text.isBlank() ? "未知错误" : text;
    }
}
