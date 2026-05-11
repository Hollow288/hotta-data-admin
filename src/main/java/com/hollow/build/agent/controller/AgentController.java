package com.hollow.build.agent.controller;

import com.hollow.build.agent.alias.AliasAgent;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.database.DatabaseAgent;
import com.hollow.build.agent.dto.AgentRequest;
import com.hollow.build.agent.dto.AgentResponse;
import com.hollow.build.agent.router.AgentRouter;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.utils.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Agent HTTP 入口。
 *
 * <ul>
 *   <li>POST /api/v1/agent/ask       —— 默认入口，走路由自动派发到 database 或 alias agent</li>
 *   <li>POST /api/v1/agent/database  —— 跳过路由，直连数据库 agent（教学/调试用）</li>
 *   <li>POST /api/v1/agent/alias     —— 跳过路由，直连别名 agent（教学/调试用）</li>
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
            description = "router 先用一次 LLM 判定派给 database 还是 alias，再由对应 agent 完成任务")
    public ApiResponse<AgentResponse> ask(@RequestBody AgentRequest req,
                                          HttpServletRequest httpRequest) throws Exception {
        String requestId = newRequestId();
        String clientIp = loginAttemptService.getClientIP(httpRequest);
        AbstractAgent.AgentResult result = agentRouter.route(req.getMessage(), requestId, clientIp);
        return ApiResponse.success(new AgentResponse(result.reply(), result.trace()));
    }

    @PostMapping("/database")
    @PublicEndpoint
    @Operation(summary = "直连数据库 Agent（跳过路由）")
    public ApiResponse<AgentResponse> askDatabase(@RequestBody AgentRequest req,
                                                  HttpServletRequest httpRequest) throws Exception {
        AbstractAgent.AgentResult result = databaseAgent.ask(
                req.getMessage(), newRequestId(), loginAttemptService.getClientIP(httpRequest));
        return ApiResponse.success(new AgentResponse(result.reply(), result.trace()));
    }

    @PostMapping("/alias")
    @PublicEndpoint
    @Operation(summary = "直连别名 Agent（跳过路由）",
            description = "返回 {\"type\":\"武器|意志|源器\",\"value\":\"正式名\"}")
    public ApiResponse<AgentResponse> askAlias(@RequestBody AgentRequest req,
                                               HttpServletRequest httpRequest) throws Exception {
        AbstractAgent.AgentResult result = aliasAgent.ask(
                req.getMessage(), newRequestId(), loginAttemptService.getClientIP(httpRequest));
        return ApiResponse.success(new AgentResponse(result.reply(), result.trace()));
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
