package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 服务配置属性类，用于绑定 application.yml 中 com.hollow.ocr 前缀下的配置项。
 *
 * <p>配置示例：
 * <pre>
 * com:
 *   hollow:
 *     ocr:
 *       service-url: http://localhost:8000/ocr   # 本地 RapidOCR 服务的接口地址
 *       result-ttl: 3600                          # OCR 结果在 Redis 中的保留时间（秒）
 *       max-file-size-bytes: 5242880              # OCR 接口允许的最大文件大小（5MB）
 *       max-retry-count: 3                        # OCR 调用失败时最多重试 3 次
 *       retry-delay-millis: 15000                 # 每次重试前等待 15 秒
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.ocr")
public class OcrConfigurationProperties {

    /**
     * RapidOCR 服务的接口地址。
     * <p>
     * 对应 RapidOCR FastAPI 服务中 {@code @app.post("/ocr")} 定义的端点，
     * 消费者会向此地址发送 multipart/form-data 格式的 POST 请求上传图片。
     */
    private String serviceUrl = "http://127.0.0.1:8000/ocr";

    /**
     * OCR 任务结果在 Redis 中的过期时间，单位为秒，默认 3600 秒（1小时）。
     * <p>
     * 过期后任务结果会被 Redis 自动删除，前端再查询时会返回"任务不存在或已过期"。
     * 可根据实际需要调整：
     * <ul>
     *   <li>设置较短（如 300）：适合实时性要求高、结果无需长期保留的场景</li>
     *   <li>设置较长（如 86400）：适合用户可能稍后再来查看结果的场景</li>
     * </ul>
     */
    private long resultTtl = 3600;

    /**
     * OCR 上传允许的最大文件大小，单位为字节。
     * 默认 5MB，避免将大文件 Base64 后塞进 MQ 导致消息体过大。
     */
    private long maxFileSizeBytes = 5L * 1024 * 1024;

    /**
     * 允许提交到 OCR 接口的 MIME 类型白名单。
     */
    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/bmp"
    ));

    /**
     * 提交消息后等待 RabbitMQ 发布确认的超时时间，单位为毫秒。
     */
    private long submitConfirmTimeoutMillis = 5000;

    /**
     * OCR 处理失败后允许的最大重试次数。
     * 例如值为 3 表示首次消费失败后，最多再进入重试队列 3 次。
     */
    private int maxRetryCount = 3;

    /**
     * 重试队列的延迟时间，单位为毫秒。
     * 消息会先进入 retry queue，TTL 到期后再回到主队列。
     */
    private long retryDelayMillis = 15000;

    /**
     * 任务允许处于 PENDING 状态的最长时间，单位为秒。
     * 超过该时间仍未被消费时，会被标记为 FAILED，避免前端无限轮询。
     */
    private long pendingTimeoutSeconds = 300;

    /**
     * 扫描超时 PENDING 任务的间隔，单位为毫秒。
     */
    private long pendingTimeoutScanIntervalMillis = 60000;
}
