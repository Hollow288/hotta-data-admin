package com.hollow.build.agent.controller;

import com.hollow.build.agent.core.DatabaseAgent;
import com.hollow.build.agent.dto.AgentRequest;
import com.hollow.build.agent.dto.AgentResponse;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 的 HTTP 入口。
 *
 * 用法：POST /api/v1/agent/ask  body = {"message": "..."}
 * 例：
 *   {"message": "blog_posts 里最新发布的 3 篇文章标题"}
 *   {"message": "role 表里有几条数据，都是什么角色？"}
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent", description = "数据库查询 Agent（教学示例）")
public class AgentController {

    private final DatabaseAgent databaseAgent;

    @PostMapping("/ask")
    @PublicEndpoint
    @Operation(summary = "向数据库 Agent 提问",
            description = "AI 会自行决定要不要调用 list_tables / describe_table / query_table 来回答你的问题")
    public ApiResponse<AgentResponse> ask(@RequestBody AgentRequest req) throws Exception {
        DatabaseAgent.AgentResult result = databaseAgent.ask(req.getMessage());
        return ApiResponse.success(new AgentResponse(result.reply(), result.trace()));
    }
}
