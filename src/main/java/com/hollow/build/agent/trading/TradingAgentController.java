package com.hollow.build.agent.trading;

import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.dto.AgentRequest;
import com.hollow.build.agent.dto.AgentResponse;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.auth.util.LoginAttemptService;
import com.hollow.build.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/trading")
@Tag(name = "Trading Agent", description = "投资 Agent 第一阶段：公开现货行情查询")
public class TradingAgentController {

    private final TradingAgent tradingAgent;
    private final LoginAttemptService loginAttemptService;

    @PostMapping
    @PublicEndpoint
    @Operation(summary = "直连 TradingAgent，debug=true 返回工具调用轨迹")
    public ApiResponse<AgentResponse> ask(@RequestBody AgentRequest request,
                                         @RequestParam(defaultValue = "false") boolean debug,
                                         HttpServletRequest httpRequest) throws Exception {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return new ApiResponse<>(400, "message 不能为空");
        }
        String requestId = UUID.randomUUID().toString().replace("-", "");
        AbstractAgent.AgentResult result = tradingAgent.ask(request.getMessage(), requestId,
                loginAttemptService.getClientIP(httpRequest));
        return ApiResponse.success(new AgentResponse(requestId, result.agentName(), result.answerText(),
                result.answerData(), debug ? result.debugTrace() : null));
    }
}
