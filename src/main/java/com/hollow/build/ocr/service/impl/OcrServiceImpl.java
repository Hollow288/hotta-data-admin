package com.hollow.build.ocr.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.ocr.config.OcrConfigurationProperties;
import com.hollow.build.config.RabbitMQConfig;
import com.hollow.build.ocr.dto.OcrTaskDto;
import com.hollow.build.ocr.service.OcrService;
import com.hollow.build.ocr.service.OcrTaskStateService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * OCR 服务实现类，充当消息队列的<b>生产者</b>角色。
 * <p>
 * 负责接收前端上传的图片，将其存储到 MinIO 临时 bucket 后，
 * 将对象路径封装成消息发送到 RabbitMQ 队列，同时在 Redis 中维护任务状态，供前端轮询查询。
 * <p>
 * 本类<b>不直接调用</b> RapidOCR 服务，实际的 OCR 识别由消费者 {@link com.hollow.build.ocr.consumer.OcrConsumer} 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic", ".heif", ".pdf"
    );

    /** 远程 OCR 服务支持的返回模式 */
    private static final Set<String> ALLOWED_MODES = Set.of("detail", "list", "text");

    private static final DateTimeFormatter OBJECT_MONTH_PATH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * RabbitTemplate 是 Spring AMQP 提供的消息发送工具，
     * 调用 convertAndSend() 方法可将 Java 对象序列化为 JSON 后发送到指定的交换机和路由键。
     * 序列化方式由 {@link RabbitMQConfig#jsonMessageConverter()} 中注册的 Jackson2JsonMessageConverter 决定。
     */
    private final RabbitTemplate rabbitTemplate;

    private final OcrTaskStateService ocrTaskStateService;
    private final OcrConfigurationProperties ocrConfig;
    private final MinioUtil minioUtil;

    /**
     * {@inheritDoc}
     * <p>
     * 实现细节：
     * <ol>
     *   <li>通过 UUID 生成唯一任务标识 taskId</li>
     *   <li>将 MultipartFile 上传到 MinIO 临时 bucket，避免将大文件内容放入 MQ 消息体</li>
     *   <li>构建消息体 Map，包含 taskId、bucketName、objectName、fileName 四个字段</li>
     *   <li>在 Redis 中写入初始状态 PENDING，key 为 "ocr:result:{taskId}"，过期时间由配置决定，
     *       同时将 taskId 加入 Redis Set（ocr:active-tasks）用于活跃任务跟踪</li>
     *   <li>通过 RabbitTemplate 将消息发送到 ocr.exchange 交换机，路由键为 ocr.task，
     *       交换机根据路由键将消息投递到 ocr.queue 队列</li>
     *   <li>消息发送完成后立即返回 taskId，不等待 OCR 处理结果</li>
     * </ol>
     */
    @Override
    public ApiResponse<OcrTaskDto> submitTask(MultipartFile file, String mode, Double minConfidence) {
        ApiResponse<OcrTaskDto> configurationError = validateConfiguration();
        if (configurationError != null) {
            return configurationError;
        }

        ApiResponse<OcrTaskDto> validationError = validateFile(file);
        if (validationError != null) {
            return validationError;
        }

        String resolvedMode = resolveMode(mode);
        if (resolvedMode == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "mode 仅支持 detail / list / text",
                    null
            );
        }

        Double resolvedMinConfidence = resolveMinConfidence(minConfidence);
        if (minConfidence != null && resolvedMinConfidence == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "minConfidence 必须在 [0, 1] 之间",
                    null
            );
        }

        String taskId = null;
        try {
            taskId = UUID.randomUUID().toString();

            // 将图片上传到 MinIO 临时存储，MQ 消息中只传对象路径，避免消息体过大
            String fileName = file.getOriginalFilename();
            String monthPath = LocalDate.now().format(OBJECT_MONTH_PATH_FORMATTER);
            String objectName = "ocr/" + monthPath + "/" + taskId + "/" + (fileName != null ? fileName : "image.png");
            String bucketName = ocrConfig.getMinioBucket();
            minioUtil.ensureBucketExists(bucketName);
            try (InputStream inputStream = file.getInputStream()) {
                minioUtil.putObject(bucketName, objectName, inputStream);
            }

            // 构建发送到 RabbitMQ 的消息体（只传路径与调用参数，不传文件内容）
            Map<String, Object> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("bucketName", bucketName);
            message.put("objectName", objectName);
            message.put("fileName", fileName);
            message.put("contentType", file.getContentType());
            message.put("mode", resolvedMode);
            if (resolvedMinConfidence != null) {
                message.put("minConfidence", resolvedMinConfidence);
            }
            message.put("retryCount", 0);

            // 在 Redis 中创建初始任务记录，状态为 PENDING，表示任务已提交但尚未被消费者处理
            OcrTaskDto pending = ocrTaskStateService.createPendingTask(taskId, resolvedMode);
            CorrelationData correlationData = new CorrelationData(taskId);

            // 将消息发送到 RabbitMQ：
            // 参数1: exchange — 交换机名称（ocr.exchange），负责根据路由键分发消息
            // 参数2: routingKey — 路由键（ocr.task），交换机据此将消息投递到绑定了该键的队列
            // 参数3: message — 消息体，会被 Jackson2JsonMessageConverter 序列化为 JSON
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OCR_EXCHANGE,
                    RabbitMQConfig.OCR_ROUTING_KEY,
                    message,
                    correlationData
            );
            waitForPublishConfirm(correlationData);

            log.info("OCR 任务已提交: taskId={}, fileName={}", taskId, file.getOriginalFilename());

            return ApiResponse.success(pending);

        } catch (Exception e) {
            if (taskId != null) {
                ocrTaskStateService.updateStatus(taskId, "FAILED", null, "提交 OCR 任务失败: " + safeMessage(e), 0);
            }
            log.error("提交 OCR 任务失败", e);
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "提交 OCR 任务失败: " + safeMessage(e),
                    null
            );
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现细节：
     * 直接从 Redis 中读取 key 为 "ocr:result:{taskId}" 的值并反序列化为 OcrTaskDto 返回。
     * 该值由消费者 {@link com.hollow.build.ocr.consumer.OcrConsumer} 在处理过程中实时更新。
     */
    @Override
    public ApiResponse<OcrTaskDto> getResult(String taskId) {
        OcrTaskDto result = ocrTaskStateService.getTask(taskId);
        if (result == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "任务不存在或已过期",
                    null
            );
        }

        if (ocrTaskStateService.isPendingTimedOut(result)) {
            result = ocrTaskStateService.markPendingTimeout(taskId);
        }

        return ApiResponse.success(result);
    }

    private ApiResponse<OcrTaskDto> validateConfiguration() {
        if (ocrConfig.getServiceUrl() == null || ocrConfig.getServiceUrl().isBlank()) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(),
                    "OCR 服务地址未配置",
                    null
            );
        }
        return null;
    }

    private ApiResponse<OcrTaskDto> validateFile(MultipartFile file) {
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
                    "OCR 图片不能超过 " + formatSize(ocrConfig.getMaxFileSizeBytes()),
                    null
            );
        }

        if (!isAllowedFileType(file)) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "仅支持 JPG/PNG/WEBP/BMP/HEIC/HEIF 图片或 PDF 文件",
                    null
            );
        }

        return null;
    }

    /**
     * 解析返回模式：传空时回落到配置默认；传非法值返回 null。
     */
    private String resolveMode(String mode) {
        String candidate = (mode == null || mode.isBlank()) ? ocrConfig.getDefaultMode() : mode;
        if (candidate == null || candidate.isBlank()) {
            return "detail";
        }
        String normalized = candidate.toLowerCase(Locale.ROOT).trim();
        return ALLOWED_MODES.contains(normalized) ? normalized : null;
    }

    /**
     * 解析置信度阈值：传空时回落到配置默认；超出 [0,1] 时返回 null（由调用方区分）。
     */
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

    private boolean isAllowedFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String normalizedType = contentType.toLowerCase(Locale.ROOT);
            if (ocrConfig.getAllowedContentTypes().stream()
                    .map(type -> type.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedType::equals)) {
                return true;
            }
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        return ALLOWED_FILE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
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
