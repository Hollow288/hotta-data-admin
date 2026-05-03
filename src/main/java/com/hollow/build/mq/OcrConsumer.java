package com.hollow.build.mq;

import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.config.RabbitMQConfig;
import com.hollow.build.dto.OcrTaskDto;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.service.OcrTaskStateService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OCR 消息队列<b>消费者</b>，监听 RabbitMQ 中的 ocr.queue 队列。
 * <p>
 * 当生产者（{@link com.hollow.build.service.impl.OcrServiceImpl}）向队列发送 OCR 任务消息后，
 * 本类会自动拉取消息并执行以下操作：
 * <ol>
 *   <li>从消息中取出 taskId、bucketName、objectName、fileName、mode、minConfidence、retryCount</li>
 *   <li>更新 Redis 中的任务状态为 PROCESSING</li>
 *   <li>从 MinIO 读取文件字节</li>
 *   <li>构建 multipart/form-data 请求体（字段名 "file"），附带 {@code X-API-KEY} 与 query 参数</li>
 *   <li>调用远程 OCR 服务（默认 http://127.0.0.1:7634/ocr）</li>
 *   <li>按 mode 解析返回值：detail / list / text 三种结构分别落到不同字段</li>
 *   <li>将最终结果（SUCCESS + 识别内容 或 FAILED + 错误信息）写回 Redis</li>
 * </ol>
 *
 * <h2>并发控制原理</h2>
 * <p>
 * 在 application.yml 中配置了以下参数来保护远程 OCR 服务：
 * <pre>
 * spring.rabbitmq.listener.simple:
 *   prefetch: 1        # 消费者每次只从队列中预取 1 条消息，处理完当前消息后才会取下一条
 *   concurrency: 1     # 初始启动 1 个消费者线程
 *   max-concurrency: 2 # 最多允许 2 个消费者线程同时运行
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrConsumer {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final RabbitTemplate rabbitTemplate;
    private final OcrTaskStateService ocrTaskStateService;
    private final OcrConfigurationProperties ocrConfig;
    private final MinioUtil minioUtil;

    /**
     * 监听 ocr.queue 队列并处理 OCR 任务。
     * <p>
     * 消息结构（来自 {@link com.hollow.build.service.impl.OcrServiceImpl}）：
     * <ul>
     *   <li><b>taskId</b>：任务唯一标识</li>
     *   <li><b>bucketName / objectName</b>：MinIO 中文件的位置</li>
     *   <li><b>fileName / contentType</b>：原始文件名与 MIME，写入 multipart Content-Disposition / Content-Type</li>
     *   <li><b>mode</b>：远程服务返回模式 detail / list / text</li>
     *   <li><b>minConfidence</b>：可选的置信度阈值（0~1）</li>
     *   <li><b>retryCount</b>：当前重试次数</li>
     * </ul>
     */
    @RabbitListener(queues = RabbitMQConfig.OCR_QUEUE)
    public void handleOcrTask(Map<String, Object> message) {
        String taskId = stringValue(message.get("taskId"));
        String bucketName = stringValue(message.get("bucketName"));
        String objectName = stringValue(message.get("objectName"));
        String fileName = stringValue(message.get("fileName"));
        String contentType = stringValue(message.get("contentType"));
        String mode = normalizeMode(stringValue(message.get("mode")));
        Double minConfidence = doubleValue(message.get("minConfidence"));
        int retryCount = intValue(message.get("retryCount"));

        if (taskId == null || taskId.isBlank() || objectName == null || objectName.isBlank()) {
            log.error("收到结构异常的 OCR 消息，已投递到死信队列: {}", message);
            publishToDeadLetter(message, "OCR 消息缺少必要字段");
            return;
        }

        // 处理前先校验任务在 Redis 中的当前状态：
        // - 记录已不存在：可能 Redis TTL 过期，前端早就拿不到了，直接丢弃避免无谓调用远程 OCR
        // - 已是终态（SUCCESS / FAILED）：调度器或上一次消费已经写入终态，
        //   这里若直接覆盖成 PROCESSING 会让任务"复活"，导致前端永远轮询到 PENDING/PROCESSING。
        OcrTaskDto current = ocrTaskStateService.getTask(taskId);
        if (current == null) {
            log.warn("OCR 任务记录已不存在（可能已过期），丢弃消息: taskId={}", taskId);
            return;
        }
        String currentStatus = current.getStatus();
        if ("SUCCESS".equals(currentStatus) || "FAILED".equals(currentStatus)) {
            log.warn("OCR 任务已处于终态 {}（可能已被调度器超时标记），丢弃此消息: taskId={}",
                    currentStatus, taskId);
            return;
        }

        log.info("开始处理 OCR 任务: taskId={}, fileName={}, mode={}, minConfidence={}, retryCount={}",
                taskId, fileName, mode, minConfidence, retryCount);

        // 更新 Redis 中任务状态为 PROCESSING，表示消费者已开始处理
        ocrTaskStateService.updateStatus(taskId, "PROCESSING", null, null, retryCount);

        try {
            // 从 MinIO 读取文件字节
            byte[] fileBytes;
            try (InputStream is = minioUtil.getObject(bucketName, objectName)) {
                fileBytes = is.readAllBytes();
            }

            // 构建 multipart/form-data 请求体
            String boundary = "----OcrBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, fileName, contentType, fileBytes);

            // 拼接 query 参数：mode 与 min_confidence
            URI endpoint = buildEndpoint(mode, minConfidence);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody));

            // 远程服务用 X-API-KEY 鉴权，未配置则跳过（开发环境可关闭鉴权）
            String apiKey = ocrConfig.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-API-KEY", apiKey);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("远程 OCR 返回异常状态码: " + response.statusCode()
                        + ", body=" + truncate(response.body(), 500));
            }

            // 远程响应统一结构：{ code, data, elapse, pages }；data 形态随 mode 变化
            Map<String, Object> responseMap = JSON.parseObject(response.body(), new TypeReference<>() {});
            applyResultByMode(taskId, mode, responseMap, retryCount);

        } catch (Exception e) {
            handleFailure(message, taskId, retryCount, e);
        }
    }

    /**
     * 按 mode 解析返回体并写回 Redis。三种 mode 对应不同 data 结构。
     */
    @SuppressWarnings("unchecked")
    private void applyResultByMode(String taskId,
                                   String mode,
                                   Map<String, Object> responseMap,
                                   int retryCount) {
        Object dataNode = responseMap.get("data");
        Integer pages = intOrNull(responseMap.get("pages"));
        Double elapse = doubleValue(responseMap.get("elapse"));

        List<OcrTaskDto.OcrResultItem> results = null;
        List<String> textList = null;
        String fullText = null;
        int count;

        switch (mode) {
            case "list" -> {
                List<Object> rawList = dataNode instanceof List ? (List<Object>) dataNode : List.of();
                textList = rawList.stream().map(this::stringValue).toList();
                count = textList.size();
            }
            case "text" -> {
                fullText = dataNode == null ? "" : stringValue(dataNode);
                count = (fullText == null || fullText.isEmpty()) ? 0 : 1;
            }
            default -> {
                List<Map<String, Object>> rawDetail = dataNode instanceof List
                        ? (List<Map<String, Object>>) dataNode
                        : List.of();
                results = rawDetail.stream()
                        .map(item -> new OcrTaskDto.OcrResultItem(
                                stringValue(item.get("text")),
                                doubleValue(item.get("confidence")),
                                bboxValue(item.get("bbox")),
                                intOrNull(item.get("page"))
                        ))
                        .toList();
                count = results.size();
            }
        }

        ocrTaskStateService.updateSuccess(taskId, mode, results, textList, fullText, pages, elapse, retryCount);
        log.info("OCR 任务完成: taskId={}, mode={}, items={}, pages={}, elapse={}s",
                taskId, mode, count, pages, elapse);
    }

    /**
     * 拼接远程服务调用 URL，附加 mode 与 min_confidence query 参数。
     */
    private URI buildEndpoint(String mode, Double minConfidence) {
        StringBuilder sb = new StringBuilder(ocrConfig.getServiceUrl());
        sb.append(ocrConfig.getServiceUrl().contains("?") ? '&' : '?');
        sb.append("mode=").append(URLEncoder.encode(mode, StandardCharsets.UTF_8));
        if (minConfidence != null) {
            sb.append("&min_confidence=").append(minConfidence);
        }
        return URI.create(sb.toString());
    }

    /**
     * 根据重试配置决定将失败任务投递到 retry queue，还是最终写入 FAILED 并进入 dead queue。
     */
    private void handleFailure(Map<String, Object> message, String taskId, int retryCount, Exception e) {
        String errorMsg = safeMessage(e);
        if (retryCount < ocrConfig.getMaxRetryCount()) {
            int nextRetryCount = retryCount + 1;
            Map<String, Object> retryMessage = new HashMap<>(message);
            retryMessage.put("retryCount", nextRetryCount);
            retryMessage.put("lastError", errorMsg);

            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.OCR_RETRY_EXCHANGE,
                        RabbitMQConfig.OCR_RETRY_ROUTING_KEY,
                        retryMessage
                );
                ocrTaskStateService.markPendingForRetry(
                        taskId,
                        nextRetryCount,
                        "OCR 处理失败，已进入第 " + nextRetryCount + " 次重试队列"
                );
                log.warn("OCR 任务处理失败，已进入重试队列: taskId={}, retryCount={}, error={}",
                        taskId, nextRetryCount, errorMsg);
                return;
            } catch (Exception retryPublishException) {
                errorMsg = "OCR 重试入队失败: " + safeMessage(retryPublishException);
                log.error("OCR 任务重试入队失败: taskId={}", taskId, retryPublishException);
            }
        }

        ocrTaskStateService.updateStatus(taskId, "FAILED", null, errorMsg, retryCount);
        publishToDeadLetter(message, errorMsg);
        log.error("OCR 任务最终失败: taskId={}, retryCount={}, error={}", taskId, retryCount, errorMsg, e);
    }

    private void publishToDeadLetter(Map<String, Object> originalMessage, String errorMsg) {
        try {
            Map<String, Object> deadMessage = new HashMap<>(originalMessage);
            deadMessage.put("lastError", errorMsg);
            deadMessage.put("failedAt", System.currentTimeMillis());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OCR_DEAD_EXCHANGE,
                    RabbitMQConfig.OCR_DEAD_ROUTING_KEY,
                    deadMessage
            );
        } catch (Exception deadLetterException) {
            log.error("OCR 死信投递失败: {}", errorMsg, deadLetterException);
        }
    }

    /**
     * 手动构建 multipart/form-data 请求体。
     * <p>
     * 远程服务的 {@code POST /ocr} 接收字段名为 {@code file} 的上传，
     * 内部根据 MIME 与魔术字节自动判断图片或 PDF。这里把消息里携带的 contentType 一并写入，
     * 不可知时回落到 {@code application/octet-stream}（远程会按文件内容嗅探）。
     */
    private byte[] buildMultipartBody(String boundary, String fileName, String contentType, byte[] fileBytes) {
        String lineEnd = "\r\n";
        String prefix = "--";

        String partContentType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;

        StringBuilder header = new StringBuilder();
        header.append(prefix).append(boundary).append(lineEnd);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName != null ? fileName : "upload.bin")
                .append("\"").append(lineEnd);
        header.append("Content-Type: ").append(partContentType).append(lineEnd);
        header.append(lineEnd);

        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = (lineEnd + prefix + boundary + prefix + lineEnd).getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        return body;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "detail";
        }
        String lower = mode.toLowerCase(Locale.ROOT).trim();
        return switch (lower) {
            case "list", "text", "detail" -> lower;
            default -> "detail";
        };
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> bboxValue(Object value) {
        if (!(value instanceof List<?> outer)) {
            return null;
        }
        return outer.stream()
                .filter(point -> point instanceof List<?>)
                .map(point -> ((List<Object>) point).stream()
                        .map(this::doubleValue)
                        .toList())
                .toList();
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

    private Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "未知错误";
        }
        return exception.getMessage();
    }
}
