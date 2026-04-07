package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 文字识别控制器，提供异步 OCR 任务的提交和结果查询接口。
 * <p>
 * 本控制器采用"提交-轮询"模式：前端先通过 submit 接口上传图片获取 taskId，
 * 然后定时调用 result 接口轮询任务状态，直到状态变为 SUCCESS 或 FAILED。
 * <p>
 * 前端调用示例：
 * <pre>
 * // 1. 提交 OCR 任务
 * POST /api/v1/ocr/submit
 * Content-Type: multipart/form-data
 * Body: file=@screenshot.png
 *
 * 响应：{ "code": 200, "data": { "taskId": "xxx-xxx", "status": "PENDING" } }
 *
 * // 2. 轮询结果（建议间隔 1~2 秒）
 * GET /api/v1/ocr/result/xxx-xxx
 *
 * 响应（处理中）：{ "code": 200, "data": { "taskId": "xxx-xxx", "status": "PROCESSING" } }
 * 响应（成功）：  { "code": 200, "data": { "taskId": "xxx-xxx", "status": "SUCCESS",
 *                  "results": [{ "text": "识别文字", "confidence": 0.95 }] } }
 * 响应（失败）：  { "code": 200, "data": { "taskId": "xxx-xxx", "status": "FAILED",
 *                  "errorMsg": "RapidOCR 返回异常状态码: 500" } }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ocr")
@Tag(name = "OCR", description = "OCR 文字识别接口")
public class OcrController {

    private final OcrService ocrService;

    /**
     * 提交 OCR 识别任务。
     * <p>
     * 接收前端上传的图片文件，将其存储到 MinIO 后，通过 RabbitMQ 队列进行异步处理。
     * 接口会立即返回一个 taskId，不会等待 OCR 识别完成。
     *
     * @param file 上传的图片文件（通过 multipart/form-data 传输，参数名为 "file"）
     * @return 包含 taskId 的响应，前端凭此 taskId 调用 {@link #getResult(String)} 轮询结果
     */
    @PostMapping("/submit")
    @PublicEndpoint
    @Operation(summary = "提交 OCR 任务", description = "上传图片，返回任务ID，通过任务ID轮询获取结果")
    public ApiResponse<OcrTaskDto> submit(@RequestParam("file") MultipartFile file) {
        return ocrService.submitTask(file);
    }

    /**
     * 查询 OCR 任务的处理结果。
     * <p>
     * 前端通过定时轮询此接口获取任务进度和识别结果。
     * 根据返回数据中的 status 字段判断当前状态：
     * <ul>
     *   <li><b>PENDING</b> — 任务在队列中排队，尚未开始处理，继续轮询</li>
     *   <li><b>PROCESSING</b> — 正在调用 RapidOCR 服务识别中，继续轮询</li>
     *   <li><b>SUCCESS</b> — 识别完成，results 字段包含文本和置信度，停止轮询</li>
     *   <li><b>FAILED</b> — 识别失败，errorMsg 字段包含错误原因，停止轮询</li>
     * </ul>
     *
     * @param taskId 提交任务时返回的任务ID
     * @return 包含任务当前状态和识别结果的响应
     */
    @GetMapping("/result/{taskId}")
    @PublicEndpoint
    @Operation(summary = "查询 OCR 结果", description = "根据任务ID查询识别结果，状态为 PENDING/PROCESSING/SUCCESS/FAILED")
    public ApiResponse<OcrTaskDto> getResult(@PathVariable String taskId) {
        return ocrService.getResult(taskId);
    }
}
