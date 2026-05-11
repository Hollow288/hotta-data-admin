package com.hollow.build.agent.core;

import com.alibaba.fastjson2.JSON;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具注册中心：装一组 Tool，对外做两件事：
 * <ol>
 *   <li>{@link #openAiFormat()} —— 把这一组工具序列化成 OpenAI 兼容的 "tools" 字段</li>
 *   <li>{@link #invoke(String, String)} —— 按 name 找到对应工具并执行</li>
 * </ol>
 *
 * <p><b>注意</b>：这个类已经不是 Spring Bean 了。每个 agent 用一个独立 ToolRegistry 实例，
 * 由 {@code AgentToolRegistries} 这个 @Configuration 显式装配。这样 DatabaseAgent
 * 和 AliasAgent 就互相看不到对方的工具，避免 AI 误调。
 */
public class ToolRegistry {

    private final Map<String, Tool> toolByName;

    public ToolRegistry(List<? extends Tool> tools) {
        this.toolByName = tools.stream()
                .collect(Collectors.toMap(Tool::name, t -> t));
    }

    public List<Map<String, Object>> openAiFormat() {
        return toolByName.values().stream()
                .map(t -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name(),
                                "description", t.description(),
                                "parameters", t.parametersSchema()
                        )
                ))
                .toList();
    }

    public String invoke(String name, String argumentsJson) {
        Tool tool = toolByName.get(name);
        if (tool == null) {
            return JSON.toJSONString(Map.of("error", "未知工具: " + name));
        }
        try {
            Map<String, Object> args = (argumentsJson == null || argumentsJson.isBlank())
                    ? Map.of()
                    : JSON.parseObject(argumentsJson);
            return tool.execute(args);
        } catch (Exception e) {
            return JSON.toJSONString(Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
