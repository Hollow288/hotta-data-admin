package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.OcrTaskDto;
import org.springframework.web.multipart.MultipartFile;


public interface OcrService {

    /**
     * 提交 OCR 识别任务。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>生成全局唯一的 taskId（UUID）</li>
     *   <li>将上传的图片文件转换为 Base64 编码字符串</li>
     *   <li>在 Redis 中创建初始任务记录，状态标记为 PENDING</li>
     *   <li>将 taskId、imageBase64、fileName 封装为消息，发送到 RabbitMQ 的 ocr.queue 队列</li>
     *   <li>立即返回 taskId 给前端，前端凭此 taskId 轮询获取结果</li>
     * </ol>
     *
     * @param file 上传的图片文件（支持 JPG、PNG 等常见图片格式）
     * @return 包含 taskId 和初始状态 PENDING 的响应，前端根据 taskId 轮询 {@link #getResult(String)}
     */
    ApiResponse<OcrTaskDto> submitTask(MultipartFile file);

    /**
     * 查询 OCR 任务的处理结果。
     * <p>
     * 前端应定时轮询此接口（建议间隔 1~2 秒），根据返回的 status 字段判断任务进度：
     * <ul>
     *   <li><b>PENDING</b>：任务已提交，正在队列中排队等待处理</li>
     *   <li><b>PROCESSING</b>：消费者已取到消息，正在调用 RapidOCR 服务进行识别</li>
     *   <li><b>SUCCESS</b>：识别完成，results 字段包含识别到的文本及置信度列表</li>
     *   <li><b>FAILED</b>：识别失败，errorMsg 字段包含具体的错误原因</li>
     * </ul>
     * <p>当状态为 SUCCESS 或 FAILED 时，前端应停止轮询。</p>
     *
     * @param taskId 提交任务时返回的任务ID
     * @return 包含任务状态及识别结果的响应，任务不存在或已过期时返回错误信息
     */
    ApiResponse<OcrTaskDto> getResult(String taskId);
}
