package com.hollow.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String serviceUrl;

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
}
