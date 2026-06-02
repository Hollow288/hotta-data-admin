package com.hollow.build.agent;

import com.hollow.build.agent.alias.AliasAgent;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.database.DatabaseAgent;
import com.hollow.build.agent.dto.AgentRequest;
import com.hollow.build.agent.dto.AgentResponse;
import com.hollow.build.agent.router.AgentRouter;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.auth.util.LoginAttemptService;
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

/**
 * Agent HTTP 入口。
 *
 * <ul>
 *   <li>POST /api/v1/agent/ask       —— 默认入口，走路由自动派发到 database 或 alias agent</li>
 *   <li>POST /api/v1/agent/database  —— 跳过路由，直连数据库 agent（教学/调试用）</li>
 *   <li>POST /api/v1/agent/alias     —— 跳过路由，直连别名 agent（教学/调试用）</li>
 *   <li>加 ?debug=true 时才返回工具调用轨迹 debugTrace</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent", description = "数据库查询 + 别名解析的多 agent 教学示例")
public class AgentController {

    private final AgentRouter agentRouter;
    private final DatabaseAgent databaseAgent;
    private final AliasAgent aliasAgent;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/ask")
    @PublicEndpoint
    @Operation(summary = "向 Agent 提问（自动路由）",
            description = "router 先用一次 function calling 判定派给哪个 agent；不支持的问题返回 400")
    public ApiResponse<AgentResponse> ask(@RequestBody AgentRequest req,
                                          @RequestParam(defaultValue = "false") boolean debug,
                                          HttpServletRequest httpRequest) throws Exception {
        String requestId = newRequestId();
        String clientIp = loginAttemptService.getClientIP(httpRequest);
        AbstractAgent.AgentResult result = agentRouter.route(req.getMessage(), requestId, clientIp);
        return ApiResponse.success(toResponse(requestId, result, debug));
    }

    @PostMapping("/database")
    @PublicEndpoint
    @Operation(summary = "直连数据库 Agent（跳过路由）")
    public ApiResponse<AgentResponse> askDatabase(@RequestBody AgentRequest req,
                                                  @RequestParam(defaultValue = "false") boolean debug,
                                                  HttpServletRequest httpRequest) throws Exception {
        String requestId = newRequestId();
        AbstractAgent.AgentResult result = databaseAgent.ask(
                req.getMessage(), requestId, loginAttemptService.getClientIP(httpRequest));
        return ApiResponse.success(toResponse(requestId, result, debug));
    }

    @PostMapping("/alias")
    @PublicEndpoint
    @Operation(summary = "直连别名 Agent（跳过路由）",
            description = "answerData 返回 {\"type\":\"武器|意志|源器\",\"value\":\"正式名\"}")
    public ApiResponse<AgentResponse> askAlias(@RequestBody AgentRequest req,
                                               @RequestParam(defaultValue = "false") boolean debug,
                                               HttpServletRequest httpRequest) throws Exception {
        String requestId = newRequestId();
        AbstractAgent.AgentResult result = aliasAgent.ask(
                req.getMessage(), requestId, loginAttemptService.getClientIP(httpRequest));
        return ApiResponse.success(toResponse(requestId, result, debug));
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static AgentResponse toResponse(String requestId, AbstractAgent.AgentResult result, boolean debug) {
        return new AgentResponse(
                requestId,
                result.agentName(),
                result.answerText(),
                result.answerData(),
                debug ? result.debugTrace() : null
        );
    }
}
