package com.hollow.build.mq;

import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.utils.RedisUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OCR 消息队列<b>消费者</b>，监听 RabbitMQ 中的 ocr.queue 队列。
 * <p>
 * 当生产者（{@link com.hollow.build.service.impl.OcrServiceImpl}）向队列发送 OCR 任务消息后，
 * 本类会自动拉取消息并执行以下操作：
 * <ol>
 *   <li>从消息中取出 taskId、imageBase64、fileName</li>
 *   <li>更新 Redis 中的任务状态为 PROCESSING</li>
 *   <li>将 Base64 字符串还原为图片字节数组</li>
 *   <li>构建 multipart/form-data 格式的 HTTP 请求体（字段名 "file"，与 RapidOCR FastAPI 的 UploadFile 参数匹配）</li>
 *   <li>通过 HttpClient 将请求发送到本地 RapidOCR 服务（默认 http://localhost:8000/ocr）</li>
 *   <li>解析 RapidOCR 返回的 JSON 响应，提取识别文本和置信度</li>
 *   <li>将最终结果（SUCCESS + 识别内容 或 FAILED + 错误信息）写回 Redis</li>
 * </ol>
 *
 * <h2>并发控制原理</h2>
 * <p>
 * 在 application.yml 中配置了以下参数来保护本地 OCR 服务：
 * <pre>
 * spring.rabbitmq.listener.simple:
 *   prefetch: 1        # 消费者每次只从队列中预取 1 条消息，处理完当前消息后才会取下一条
 *   concurrency: 1     # 初始启动 1 个消费者线程
 *   max-concurrency: 2 # 最多允许 2 个消费者线程同时运行
 * </pre>
 * 这意味着即使队列中积压了大量任务，同一时间最多只有 2 个 OCR 请求在被处理，
 * 其余请求安全地排队等待，不会压垮本地 OCR 服务的 CPU/内存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrConsumer {

    /** Redis key 前缀，与 OcrServiceImpl 中保持一致 */
    private static final String OCR_RESULT_PREFIX = "ocr:result:";

    private final RedisUtil redisUtil;
    private final OcrConfigurationProperties ocrConfig;

    /**
     * 监听 ocr.queue 队列并处理 OCR 任务。
     * <p>
     * 方法参数 message 由 Spring AMQP 自动从队列消息中反序列化而来（通过 Jackson2JsonMessageConverter），
     * 其结构为 Map，包含以下字段：
     * <ul>
     *   <li><b>taskId</b>：任务唯一标识</li>
     *   <li><b>imageBase64</b>：图片的 Base64 编码字符串</li>
     *   <li><b>fileName</b>：原始文件名</li>
     * </ul>
     *
     * @param message 从 RabbitMQ 队列中消费到的消息，由生产者 OcrServiceImpl 发送
     */
    @RabbitListener(queues = "ocr.queue")
    public void handleOcrTask(Map<String, String> message) {
        String taskId = message.get("taskId");
        String imageBase64 = message.get("imageBase64");
        String fileName = message.get("fileName");

        log.info("开始处理 OCR 任务: taskId={}, fileName={}", taskId, fileName);

        // 更新 Redis 中任务状态为 PROCESSING，表示消费者已开始处理
        updateStatus(taskId, "PROCESSING", null, null);

        try {
            // 将 Base64 还原为原始图片字节数组
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

            // 构建 multipart/form-data 请求体，用于模拟文件上传
            // boundary 是 multipart 协议中的分隔符，用于标记各个表单字段的边界
            String boundary = "----OcrBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, fileName, imageBytes);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            // 向本地 RapidOCR 服务发送 POST 请求
            // Content-Type 必须包含 boundary，服务端据此解析 multipart 请求体
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ocrConfig.getServiceUrl()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 根据 HTTP 状态码判断请求是否成功
            if (response.statusCode() == 200) {
                // RapidOCR 返回的 JSON 结构示例：
                // {
                //   "code": 200,
                //   "data": [
                //     { "text": "识别到的文字", "confidence": 0.95 },
                //     { "text": "第二行文字", "confidence": 0.88 }
                //   ],
                //   "elapse": 1.23
                // }
                Map<String, Object> responseMap = JSON.parseObject(response.body(), new TypeReference<>() {});
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseMap.get("data");

                // 将 RapidOCR 的原始响应转换为 OcrResultItem 列表
                List<OcrTaskDto.OcrResultItem> results = List.of();
                if (dataList != null) {
                    results = dataList.stream()
                            .map(item -> new OcrTaskDto.OcrResultItem(
                                    (String) item.get("text"),
                                    ((Number) item.get("confidence")).doubleValue()
                            ))
                            .toList();
                }

                // 识别成功，将结果写入 Redis，前端下次轮询即可获取
                updateStatus(taskId, "SUCCESS", results, null);
                log.info("OCR 任务完成: taskId={}, 识别到 {} 条文本", taskId, results.size());

            } else {
                String errorMsg = "RapidOCR 返回异常状态码: " + response.statusCode();
                updateStatus(taskId, "FAILED", null, errorMsg);
                log.error("OCR 任务失败: taskId={}, {}", taskId, errorMsg);
            }

        } catch (Exception e) {
            // 任何异常（网络超时、OCR 服务未启动、JSON 解析失败等）都标记为 FAILED
            updateStatus(taskId, "FAILED", null, e.getMessage());
            log.error("OCR 任务异常: taskId={}", taskId, e);
        }
    }

    /**
     * 更新 Redis 中指定任务的状态和结果。
     * <p>
     * 每次状态变更都会重新设置过期时间，确保从最后一次更新开始计算 TTL。
     *
     * @param taskId   任务ID
     * @param status   新状态（PENDING / PROCESSING / SUCCESS / FAILED）
     * @param results  识别结果列表，仅在 SUCCESS 时有值
     * @param errorMsg 错误信息，仅在 FAILED 时有值
     */
    private void updateStatus(String taskId, String status, List<OcrTaskDto.OcrResultItem> results, String errorMsg) {
        OcrTaskDto dto = OcrTaskDto.builder()
                .taskId(taskId)
                .status(status)
                .results(results)
                .errorMsg(errorMsg)
                .build();
        redisUtil.set(OCR_RESULT_PREFIX + taskId, JSON.toJSONString(dto), ocrConfig.getResultTtl());
    }

    /**
     * 手动构建 multipart/form-data 格式的 HTTP 请求体。
     * <p>
     * 由于 Java 原生 HttpClient 不内置 multipart 支持，需要手动拼装。
     * 构建的请求体格式如下（符合 RFC 2046 标准）：
     * <pre>
     * ------OcrBoundary1234567890\r\n
     * Content-Disposition: form-data; name="file"; filename="image.png"\r\n
     * Content-Type: application/octet-stream\r\n
     * \r\n
     * [图片二进制数据]
     * \r\n
     * ------OcrBoundary1234567890--\r\n
     * </pre>
     * 其中 name="file" 与 RapidOCR FastAPI 端的参数名一致：
     * {@code async def ocr_process(file: UploadFile = File(...))}
     *
     * @param boundary  multipart 分隔符，需与 Content-Type header 中的 boundary 保持一致
     * @param fileName  原始文件名，写入 Content-Disposition 的 filename 字段
     * @param fileBytes 图片的原始字节数组
     * @return 完整的 multipart 请求体字节数组
     */
    private byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes) {
        String lineEnd = "\r\n";
        String prefix = "--";

        // 构建 multipart 头部：分隔符 + Content-Disposition + Content-Type + 空行
        StringBuilder header = new StringBuilder();
        header.append(prefix).append(boundary).append(lineEnd);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName != null ? fileName : "image.png")
                .append("\"").append(lineEnd);
        header.append("Content-Type: application/octet-stream").append(lineEnd);
        header.append(lineEnd);

        byte[] headerBytes = header.toString().getBytes();
        // 构建 multipart 尾部：换行 + 结束分隔符（以 -- 结尾表示整个 multipart 结束）
        byte[] footerBytes = (lineEnd + prefix + boundary + prefix + lineEnd).getBytes();

        // 将头部、文件内容、尾部拼接为完整的请求体
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        return body;
    }
}
