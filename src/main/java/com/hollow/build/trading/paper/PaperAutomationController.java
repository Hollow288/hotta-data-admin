package com.hollow.build.trading.paper;

import com.hollow.build.auth.util.AuthenticatedUserIdProvider;
import com.hollow.build.auth.util.LoginAttemptService;
import com.hollow.build.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static com.hollow.build.trading.paper.PaperAutomationModels.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trading/paper/accounts/{accountId}")
@Tag(name = "Paper Trading Automation", description = "账户自动运行配置与持久化日报，均需登录")
public class PaperAutomationController {
    private final PaperAutomationService automation;
    private final PaperDailyReportService reports;
    private final AuthenticatedUserIdProvider users;
    private final LoginAttemptService login;

    public record ConfigureAutomation(Boolean enabled, String dailyRunTime) {}

    @GetMapping("/automation")
    public ApiResponse<Automation> settings(@PathVariable String accountId) {
        return ApiResponse.success(automation.get(owner(), accountId));
    }

    @PutMapping("/automation")
    @Operation(summary = "配置此账户是否自动运行及每天北京时间 HH:mm")
    public ApiResponse<Automation> configure(@PathVariable String accountId, @RequestBody ConfigureAutomation request) {
        return ApiResponse.success(automation.configure(owner(), accountId, request.enabled(), request.dailyRunTime()));
    }

    @PostMapping("/automation/run-today")
    @Operation(summary = "立即验收今天的自动流程，与定时触发共享幂等记录，不受配置时间限制")
    public ApiResponse<AutomationResult> runToday(@PathVariable String accountId,
                                                  @RequestParam(defaultValue = "false") boolean debug,
                                                  HttpServletRequest request) {
        AutomationResult result = automation.runToday(owner(), accountId, login.getClientIP(request));
        return ApiResponse.success(new AutomationResult(PaperTradingController.view(result.run(), debug), result.report()));
    }

    @PostMapping("/reports/today")
    @Operation(summary = "汇总今天截至当前的账本并保存日报，不调用模型、不交易")
    public ApiResponse<DailyReport> generate(@PathVariable String accountId) {
        return ApiResponse.success(reports.generateToday(owner(), accountId));
    }

    @GetMapping("/reports")
    public ApiResponse<List<DailyReport>> list(@PathVariable String accountId,
                                              @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(reports.list(owner(), accountId, limit));
    }

    @GetMapping("/reports/latest")
    public ApiResponse<DailyReport> latest(@PathVariable String accountId) {
        var list = reports.list(owner(), accountId, 1);
        if (list.isEmpty()) throw new PaperTradingException(404, "该账户尚未生成日报");
        return ApiResponse.success(list.getFirst());
    }

    @GetMapping("/reports/{date}")
    public ApiResponse<DailyReport> report(@PathVariable String accountId, @PathVariable String date) {
        return ApiResponse.success(reports.get(owner(), accountId, date));
    }

    private long owner() {
        Long id;
        try { id = users.getUserId(); } catch (NumberFormatException error) { id = null; }
        if (id == null) throw new AuthenticationCredentialsNotFoundException("请先登录");
        return id;
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
        return new ApiResponse<>(503, "模拟账本数据库不可用，请检查连接并重新执行 docs/sql/trading_paper.sql 补建自动化和日报表");
    }
}
