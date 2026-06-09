package com.hollow.build.fund;

import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.fund.config.FundConfigurationProperties;
import com.hollow.build.fund.dto.AddFundRequest;
import com.hollow.build.fund.dto.FundReviewDto;
import com.hollow.build.fund.dto.FundSnapshotSyncResultDto;
import com.hollow.build.fund.dto.FundTrendDto;
import com.hollow.build.ratelimit.BypassRateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 基金监控控制器，提供给外部（如 QQ 机器人）调用：
 * <ul>
 *   <li>查询类（公开免登录）：监控列表、净值涨跌走势、最新净值；</li>
 *   <li>管理类（公开免登录，但需 {@code X-FUND-TOKEN} 密钥）：添加监控、取消监控。</li>
 * </ul>
 * <p>注：Security 只放行 GET/POST 公开接口，故写操作统一使用 POST。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fund")
@Tag(name = "基金监控", description = "基金净值涨跌监控")
public class FundController {

    private final FundService fundService;
    private final FundConfigurationProperties fundConfig;

    /**
     * 查询监控中的基金列表。
     *
     * @return 监控中的基金
     */
    @GetMapping("/list")
    @Operation(summary = "监控基金列表", description = "查询所有监控中的基金")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<Fund>> list() {
        return ApiResponse.success(fundService.listMonitored());
    }

    /**
     * 查询某基金近若干天的净值涨跌走势（供画图）。
     *
     * @param code 基金代码
     * @param days 天数区间，默认 90
     * @return 涨跌走势
     */
    @GetMapping("/{code}/trend")
    @Operation(summary = "基金涨跌走势", description = "查询某基金近 N 天的净值涨跌走势")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<FundTrendDto> trend(@PathVariable("code") String code,
                                           @RequestParam(value = "days", defaultValue = "90") Integer days) {
        FundTrendDto dto = fundService.getTrend(code, days);
        if (dto == null) {
            return new ApiResponse<>(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "该基金未在监控列表中，请先添加", null);
        }
        return ApiResponse.success(dto);
    }

    /**
     * 查询某基金最新净值与当日涨跌。
     *
     * @param code 基金代码
     * @return 最新净值信息
     */
    @GetMapping("/{code}/latest")
    @Operation(summary = "基金最新净值", description = "查询某基金最新净值与当日涨跌")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<FundTrendDto> latest(@PathVariable("code") String code) {
        FundTrendDto dto = fundService.getLatest(code);
        if (dto == null) {
            return new ApiResponse<>(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "该基金未在监控列表中，请先添加", null);
        }
        return ApiResponse.success(dto);
    }

    /**
     * 查询某基金的点评数据包（阶段涨跌 + 回撤/波动 + 排名/规模/经理 + 近期走势），供 AI 点评。
     *
     * @param code 基金代码
     * @return 点评数据包
     */
    @GetMapping("/{code}/review")
    @Operation(summary = "基金点评数据", description = "聚合阶段涨跌/回撤/排名/经理等，打包供 AI 点评")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<FundReviewDto> review(@PathVariable("code") String code) {
        FundReviewDto dto = fundService.getReview(code);
        if (dto == null) {
            return new ApiResponse<>(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "该基金未在监控列表中，请先添加", null);
        }
        return ApiResponse.success(dto);
    }

    /**
     * 添加监控基金（需 X-FUND-TOKEN 密钥）。
     *
     * @param request 含基金代码
     * @param token   请求头 X-FUND-TOKEN
     * @return 添加成功的基金；代码无效返回 400
     */
    @PostMapping
    @Operation(summary = "添加监控基金", description = "添加一只基金到监控列表，并回补历史净值")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<Fund> add(@RequestBody AddFundRequest request,
                                 @RequestHeader(value = "X-FUND-TOKEN", required = false) String token) {
        requireToken(token);
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            return new ApiResponse<>(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "基金代码不能为空", null);
        }
        Fund fund = fundService.addFund(request.getCode().trim());
        if (fund == null) {
            return new ApiResponse<>(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "未找到该基金代码，请检查后重试", null);
        }
        return ApiResponse.success(fund);
    }

    /**
     * 手动触发所有监控中基金的同步（需 X-FUND-TOKEN 密钥）。
     * <p>用于部署新表后立即回填阶段排名等快照，不必等待定时任务。</p>
     *
     * @param token 请求头 X-FUND-TOKEN
     * @return 操作结果
     */
    @PostMapping("/sync")
    @Operation(summary = "手动同步基金数据", description = "同步所有监控中的基金净值、画像、阶段排名")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<Void> sync(@RequestHeader(value = "X-FUND-TOKEN", required = false) String token) {
        requireToken(token);
        fundService.syncAllLatest();
        return ApiResponse.success();
    }

    /**
     * 手动刷新单只基金扩展快照（需 X-FUND-TOKEN 密钥）。
     *
     * @param code  基金代码
     * @param token 请求头 X-FUND-TOKEN
     * @return 本次刷新拉到并写入的条数
     */
    @PostMapping("/{code}/sync-snapshot")
    @Operation(summary = "手动同步单只基金扩展快照", description = "只刷新阶段排名，不拉净值")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<FundSnapshotSyncResultDto> syncSnapshot(@PathVariable("code") String code,
                                                               @RequestHeader(value = "X-FUND-TOKEN", required = false) String token) {
        requireToken(token);
        FundSnapshotSyncResultDto result = fundService.syncSnapshot(code);
        if (result == null) {
            return new ApiResponse<>(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "该基金未在监控列表中，请先添加", null);
        }
        return ApiResponse.success(result);
    }

    /**
     * 取消监控某基金（需 X-FUND-TOKEN 密钥，软停用保留历史）。
     *
     * @param code  基金代码
     * @param token 请求头 X-FUND-TOKEN
     * @return 操作结果
     */
    @PostMapping("/{code}/disable")
    @Operation(summary = "取消监控基金", description = "停用某基金的监控（保留历史净值）")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<Void> disable(@PathVariable("code") String code,
                                     @RequestHeader(value = "X-FUND-TOKEN", required = false) String token) {
        requireToken(token);
        fundService.removeFund(code);
        return ApiResponse.success();
    }

    /**
     * 校验写接口密钥：配置了 {@code api-token} 时必须匹配，否则放行（适合内网/自用）。
     *
     * @param token 请求头携带的密钥
     */
    private void requireToken(String token) {
        String expected = fundConfig.getApiToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(token)) {
            throw new AuthenticationCredentialsNotFoundException("X-FUND-TOKEN 校验未通过");
        }
    }
}
