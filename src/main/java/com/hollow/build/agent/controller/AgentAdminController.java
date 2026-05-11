package com.hollow.build.agent.controller;

import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 运营开关 Admin 接口。
 *
 * <p>变更只在内存里，进程重启后会回到 yml 默认值。生产环境通常需要把
 * {@code @PublicEndpoint} 去掉、配上鉴权。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/admin")
@Tag(name = "Agent Admin", description = "Agent 运营开关")
public class AgentAdminController {

    private final AgentSwitches agentSwitches;

    @GetMapping("/switches")
    @PublicEndpoint
    @Operation(summary = "查看所有 agent 开关状态")
    public ApiResponse<Map<String, Boolean>> list() {
        return ApiResponse.success(agentSwitches.snapshot());
    }

    @PostMapping("/switches")
    @PublicEndpoint
    @Operation(summary = "翻转某个 agent 开关",
            description = "body: {\"agent\":\"alias\",\"enabled\":false}")
    public ApiResponse<Map<String, Boolean>> toggle(@RequestBody ToggleRequest req) {
        if (req.getAgent() == null || req.getAgent().isBlank()) {
            return new ApiResponse<>(400, "agent 不能为空");
        }
        agentSwitches.setEnabled(req.getAgent(), req.isEnabled());
        return ApiResponse.success(agentSwitches.snapshot());
    }

    @Data
    public static class ToggleRequest {
        /** agent 名，对应 AbstractAgent.agentName()（如 database / alias） */
        private String agent;
        /** true=开启，false=关闭 */
        private boolean enabled;
    }
}
