package com.hollow.build.ocr.config;

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
 *       service-url: http://127.0.0.1:7634/ocr    # 远程 OCR 服务的 multipart 上传端点
 *       api-key: my_default_secret                # 远程服务 X-API-KEY 鉴权值
 *       default-mode: detail                      # 默认返回模式：detail / list / text
 *       result-ttl: 3600                          # OCR 结果在 Redis 中的保留时间（秒）
 *       max-file-size-bytes: 52428800             # OCR 接口允许的最大文件大小（50MB）
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
     * 远程 OCR 服务的 multipart 上传端点（对应 README 中的 {@code POST /ocr}）。
     * <p>
     * 消费者会向此地址发送 multipart/form-data 请求上传文件，并通过 query string 附加
     * {@code mode}、{@code min_confidence} 等参数。
     */
    private String serviceUrl = "http://127.0.0.1:7634/ocr";

    /**
     * 远程 OCR 服务的 API Key，会以 {@code X-API-KEY} 请求头形式带上。
     * 未配置时跳过该请求头（适合鉴权关闭的开发环境）。
     */
    private String apiKey;

    /**
     * 默认返回模式：{@code detail} / {@code list} / {@code text}。
     * 提交任务时若未显式指定 mode，则使用该值。
     */
    private String defaultMode = "detail";

    /**
     * 默认置信度阈值（0~1）。null 表示不过滤；提交任务时若未显式传入则使用该值。
     */
    private Double defaultMinConfidence;

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
     * 默认 50MB，与远程服务默认 {@code UPLOAD_MAX_BYTES} 对齐。
     */
    private long maxFileSizeBytes = 50L * 1024 * 1024;

    /**
     * 允许提交到 OCR 接口的 MIME 类型白名单。
     * 远程服务支持 JPG / PNG / BMP / WebP / HEIC / HEIF / PDF。
     */
    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/bmp",
            "image/heic",
            "image/heif",
            "application/pdf"
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
     * OCR 临时图片存储的 MinIO bucket 名称。
     * 上传的图片会暂存到该 bucket，MQ 消息中只传递对象路径，避免消息体过大。
     */
    private String minioBucket = "ocr-temp";

    /**
     * 任务允许处于 PENDING 状态的最长时间，单位为秒。
     * 超过该时间仍未被消费时，会被标记为 FAILED，避免前端无限轮询。
     */
    private long pendingTimeoutSeconds = 300;

    /**
     * 扫描超时 PENDING 任务的间隔，单位为毫秒。
     */
    private long pendingTimeoutScanIntervalMillis = 60000;

    /**
     * 每个客户端 IP 每天允许调用 OCR 提交接口的最大次数。
     * <p>
     * 仅限制 {@code POST /api/v1/ocr/submit}；查询结果接口不受此限制。
     */
    private int dailyIpLimit = 20;
}
