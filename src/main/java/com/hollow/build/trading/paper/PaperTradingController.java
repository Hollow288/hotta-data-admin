package com.hollow.build.trading.paper;

import com.hollow.build.auth.util.AuthenticatedUserIdProvider;
import com.hollow.build.auth.util.LoginAttemptService;
import com.hollow.build.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.math.BigDecimal;
import java.util.List;

import static com.hollow.build.trading.paper.PaperModels.*;

/** 不标记 PublicEndpoint；账户所有权来自登录身份，调用方不能指定别人的 ownerId。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trading/paper/accounts")
@Tag(name = "Paper Trading", description = "虚拟 USDT 实验：账户、单次自主决策、模拟成交和账本")
public class PaperTradingController {
    private final PaperTradingService service;
    private final AuthenticatedUserIdProvider users;
    private final LoginAttemptService login;
    private final PaperProtectionService protection;

    public record CreateAccount(String name, BigDecimal initialCashUsdt) {}
    public record RunRequest(String idempotencyKey) {}
    public record ProtectionRequest(BigDecimal stopLossPrice, BigDecimal takeProfitPrice) {}

    @GetMapping("/{accountId}/equity")
    @Operation(summary = "查询已记录的完整估值采样，按账本顺序倒序；不是持续行情")
    public ApiResponse<List<EquitySample>> equity(@PathVariable String accountId,
                                                 @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(service.equitySamples(owner(), accountId, limit));
    }

    @PutMapping("/{accountId}/positions/{symbol}/protection")
    @Operation(summary = "替换持仓保护阈值，null 清除对应阈值；达到条件时可能立即触发退出")
    public ApiResponse<Position> protection(@PathVariable String accountId, @PathVariable String symbol,
                                           @RequestBody ProtectionRequest request) {
        return ApiResponse.success(protection.configure(owner(), accountId, symbol, request.stopLossPrice(), request.takeProfitPrice()));
    }

    @PostMapping("/{accountId}/protection/check")
    @Operation(summary = "立即执行纯 Java 止盈止损巡检，无模型调用；未触线返回空数组")
    public ApiResponse<List<Run>> checkProtection(@PathVariable String accountId,
                                                @RequestParam(defaultValue = "false") boolean debug) {
        return ApiResponse.success(protection.check(owner(), accountId).stream().map(run -> view(run, debug)).toList());
    }

    @ExceptionHandler(com.hollow.build.trading.market.BybitMarketException.class)
    public ApiResponse<Void> protectionMarketFailure(com.hollow.build.trading.market.BybitMarketException error) {
        return new ApiResponse<>(502, error.getMessage());
    }

    @PostMapping
    @Operation(summary = "创建虚拟 USDT 账户，规则在创建时冻结")
    public ApiResponse<Account> create(@RequestBody CreateAccount request) {
        return ApiResponse.success(service.create(owner(), request.name(), request.initialCashUsdt()));
    }

    @GetMapping
    @Operation(summary = "查询自己的模拟账户")
    public ApiResponse<List<Account>> accounts() { return ApiResponse.success(service.accounts(owner())); }

    @GetMapping("/{accountId}")
    public ApiResponse<Account> account(@PathVariable String accountId) {
        return ApiResponse.success(service.account(owner(), accountId));
    }

    @PostMapping("/{accountId}/runs")
    @Operation(summary = "手动触发一次完整实验；重试必须沿用同一 idempotencyKey")
    public ApiResponse<Run> run(@PathVariable String accountId, @RequestBody RunRequest request,
                               @RequestParam(defaultValue = "false") boolean debug, HttpServletRequest servletRequest) {
        return ApiResponse.success(view(service.runOnce(owner(), accountId, request.idempotencyKey(), login.getClientIP(servletRequest)), debug));
    }

    @GetMapping("/{accountId}/runs")
    public ApiResponse<List<Run>> runs(@PathVariable String accountId,
                                      @RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "false") boolean debug) {
        return ApiResponse.success(service.runs(owner(), accountId, limit).stream().map(r -> view(r, debug)).toList());
    }

    @GetMapping("/{accountId}/runs/{runId}")
    public ApiResponse<Run> run(@PathVariable String accountId, @PathVariable String runId,
                               @RequestParam(defaultValue = "false") boolean debug) {
        return ApiResponse.success(view(service.run(owner(), accountId, runId), debug));
    }

    private long owner() {
        Long id;
        try { id = users.getUserId(); } catch (NumberFormatException e) { id = null; }
        if (id == null) throw new AuthenticationCredentialsNotFoundException("请先登录");
        return id;
    }

    static Run view(Run run, boolean debug) {
        return debug ? run : new Run(run.runId(), run.accountId(), run.idempotencyKey(), run.status(), run.rules(),
                run.decision(), run.fill(), run.before(), run.after(), run.error(), List.of(),
                run.startedAtEpochMillis(), run.completedAtEpochMillis());
    }

    @ExceptionHandler(PaperTradingException.class)
    public ApiResponse<Void> businessFailure(PaperTradingException error) {
        return new ApiResponse<>(error.getCode(), error.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ApiResponse<Void> invalidRequest(Exception error) {
        return new ApiResponse<>(400, error instanceof IllegalArgumentException ? error.getMessage() : "请求体或参数格式错误");
    }

    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> databaseFailure(DataAccessException error) {
        return new ApiResponse<>(503, "模拟账本数据库不可用，请确认数据库连接，并先执行 docs/sql/trading_paper.sql 建表");
    }
}
