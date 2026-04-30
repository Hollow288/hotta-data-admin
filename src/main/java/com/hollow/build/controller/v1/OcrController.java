package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.service.OcrService;
import com.hollow.build.utils.LoginAttemptService;
import com.hollow.build.utils.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * OCR 文字识别控制器，提供异步 OCR 任务的提交和结果查询接口。
 * <p>
 * 本控制器采用"提交-轮询"模式：前端先通过 submit 接口上传图片获取 taskId，
 * 然后定时调用 result 接口轮询任务状态，直到状态变为 SUCCESS 或 FAILED。
 * <p>
 * 前端调用示例：
 * <pre>
 * // 1. 提交 OCR 任务（mode、minConfidence 可选）
 * POST /api/v1/ocr/submit?mode=text&minConfidence=0.8
 * Content-Type: multipart/form-data
 * Body: file=@screenshot.png
 *
 * 响应：{ "code": 200, "data": { "taskId": "xxx-xxx", "status": "PENDING" } }
 *
 * // 2. 轮询结果（建议间隔 1~2 秒）
 * GET /api/v1/ocr/result/xxx-xxx
 *
 * 响应（处理中）：{ "code": 200, "data": { "taskId": "xxx-xxx", "status": "PROCESSING" } }
 * 响应（detail 成功）：{ "code": 200, "data": { "status": "SUCCESS", "mode": "detail",
 *                       "results": [{ "text": "...", "confidence": 0.95, "bbox": [...], "page": 1 }] } }
 * 响应（list 成功）：  { "code": 200, "data": { "status": "SUCCESS", "mode": "list",
 *                       "textList": ["第一段", "第二段"] } }
 * 响应（text 成功）：  { "code": 200, "data": { "status": "SUCCESS", "mode": "text",
 *                       "fullText": "拼接后的完整字符串" } }
 * 响应（失败）：       { "code": 200, "data": { "status": "FAILED", "errorMsg": "..." } }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ocr")
@Tag(name = "OCR", description = "OCR 文字识别接口")
public class OcrController {

    private static final String RATE_LIMIT_KEY_PREFIX = "ocr:rate-limit:";
    private static final long ONE_DAY_SECONDS = 86400L;

    private final OcrService ocrService;
    private final OcrConfigurationProperties ocrConfig;
    private final LoginAttemptService loginAttemptService;
    private final RedisUtil redisUtil;

    /**
     * 提交 OCR 识别任务。
     * <p>
     * 接收前端上传的图片或 PDF 文件，将其存储到 MinIO 后，通过 RabbitMQ 队列进行异步处理。
     * 接口会立即返回一个 taskId，不会等待 OCR 识别完成。
     * <p>
     * 受 IP 维度限流保护：每个客户端 IP 每天最多调用 {@link OcrConfigurationProperties#getDailyIpLimit()} 次，
     * 超出后直接返回 {@link GlobalErrorCodeConstants#TOO_MANY_REQUESTS}，不会占用 MinIO / MQ 资源。
     *
     * @param file          上传的图片或 PDF 文件（multipart/form-data，参数名 "file"）
     * @param mode          可选返回模式：detail / list / text，缺省走配置默认值
     * @param minConfidence 可选置信度阈值（0~1），仅返回置信度 ≥ 该值的条目
     * @param request       HTTP 请求对象，用于读取真实客户端 IP 做限流
     * @return 包含 taskId 的响应，前端凭此 taskId 调用 {@link #getResult(String)} 轮询结果
     */
    @PostMapping("/submit")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "提交 OCR 任务", description = "上传图片/PDF，返回任务ID，通过任务ID轮询获取结果")
    public ApiResponse<OcrTaskDto> submit(
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "返回模式：detail / list / text") @RequestParam(value = "mode", required = false) String mode,
            @Parameter(description = "置信度阈值（0~1），仅返回置信度 ≥ 该值的条目")
            @RequestParam(value = "minConfidence", required = false) Double minConfidence,
            HttpServletRequest request) {
        ApiResponse<OcrTaskDto> rateLimited = checkDailyIpLimit(request);
        if (rateLimited != null) {
            return rateLimited;
        }
        return ocrService.submitTask(file, mode, minConfidence);
    }

    /**
     * 查询 OCR 任务的处理结果。
     * <p>
     * 前端通过定时轮询此接口获取任务进度和识别结果。
     * 根据返回数据中的 status 字段判断当前状态：
     * <ul>
     *   <li><b>PENDING</b> — 任务在队列中排队，尚未开始处理，继续轮询</li>
     *   <li><b>PROCESSING</b> — 正在调用远程 OCR 服务识别中，继续轮询</li>
     *   <li><b>SUCCESS</b> — 识别完成，按 mode 字段读取 results / textList / fullText</li>
     *   <li><b>FAILED</b> — 识别失败，errorMsg 字段包含错误原因，停止轮询</li>
     * </ul>
     *
     * @param taskId 提交任务时返回的任务ID
     * @return 包含任务当前状态和识别结果的响应
     */
    @GetMapping("/result/{taskId}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询 OCR 结果", description = "根据任务ID查询识别结果，状态为 PENDING/PROCESSING/SUCCESS/FAILED")
    public ApiResponse<OcrTaskDto> getResult(@PathVariable String taskId) {
        return ocrService.getResult(taskId);
    }

    /**
     * 校验当前 IP 当天是否已超过提交上限。
     * <p>
     * 借助 Redis INCR 原子计数：以 {@code ocr:rate-limit:{ip}:{yyyyMMdd}} 为键计数，
     * 首次创建时设置 24 小时 TTL；命中限额返回 429 业务码，否则返回 null 放行。
     * Redis 不可用或拿不到 IP 时按"放行"处理，避免因依赖故障误伤正常请求。
     */
    private ApiResponse<OcrTaskDto> checkDailyIpLimit(HttpServletRequest request) {
        String ip = loginAttemptService.getClientIP(request);
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String key = RATE_LIMIT_KEY_PREFIX + ip + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = redisUtil.increment(key, 1L);
        if (count == null) {
            return null;
        }
        if (count == 1L) {
            redisUtil.expire(key, ONE_DAY_SECONDS);
        }
        int limit = ocrConfig.getDailyIpLimit();
        if (count > limit) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getCode(),
                    "OCR 调用次数已达上限（每个 IP 每天 " + limit + " 次），请明日再试",
                    null
            );
        }
        return null;
    }
}
