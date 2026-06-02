package com.hollow.build.ocr.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ocr.dto.OcrTaskDto;
import org.springframework.web.multipart.MultipartFile;


public interface OcrService {

    /**
     * 提交 OCR 识别任务。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>生成全局唯一的 taskId（UUID）</li>
     *   <li>将上传的文件写入 MinIO 临时 bucket</li>
     *   <li>在 Redis 中创建初始任务记录，状态标记为 PENDING</li>
     *   <li>将 taskId、对象路径、mode、minConfidence 等参数封装为消息，发送到 RabbitMQ 的 ocr.queue 队列</li>
     *   <li>立即返回 taskId 给前端，前端凭此 taskId 轮询获取结果</li>
     * </ol>
     *
     * @param file          上传的图片或 PDF 文件
     * @param mode          返回模式（detail / list / text），为空时使用配置的默认值
     * @param minConfidence 置信度阈值（0~1），为 null 时不过滤
     * @return 包含 taskId 和初始状态 PENDING 的响应，前端根据 taskId 轮询 {@link #getResult(String)}
     */
    ApiResponse<OcrTaskDto> submitTask(MultipartFile file, String mode, Double minConfidence);

    /**
     * 查询 OCR 任务的处理结果。
     * <p>
     * 前端应定时轮询此接口（建议间隔 1~2 秒），根据返回的 status 字段判断任务进度：
     * <ul>
     *   <li><b>PENDING</b>：任务已提交，正在队列中排队等待处理</li>
     *   <li><b>PROCESSING</b>：消费者已取到消息，正在调用远程 OCR 服务进行识别</li>
     *   <li><b>SUCCESS</b>：识别完成，按 mode 取 results / textList / fullText 中对应字段</li>
     *   <li><b>FAILED</b>：识别失败，errorMsg 字段包含具体的错误原因</li>
     * </ul>
     * <p>当状态为 SUCCESS 或 FAILED 时，前端应停止轮询。</p>
     *
     * @param taskId 提交任务时返回的任务ID
     * @return 包含任务状态及识别结果的响应，任务不存在或已过期时返回错误信息
     */
    ApiResponse<OcrTaskDto> getResult(String taskId);
}
