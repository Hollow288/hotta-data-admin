package com.hollow.build.agent.tool;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具注册中心。
 *
 * Spring 启动时把所有实现 {@link Tool} 的 Bean 自动注入进来。它做两件事：
 *   1. {@link #openAiFormat()} —— 把所有工具序列化成大模型能看懂的 "tools" 字段，
 *      跟随每次 chat completion 请求一起发给 AI。
 *   2. {@link #invoke(String, String)} —— AI 决定调用某个工具时，按 name 找到对应
 *      实现并执行；执行失败就把错误信息也以 JSON 返回给 AI。
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolByName;

    public ToolRegistry(List<Tool> tools) {
        this.toolByName = tools.stream()
                .collect(Collectors.toMap(Tool::name, t -> t));
    }

    /**
     * OpenAI 兼容格式（DeepSeek、Qwen、Doubao、Kimi 等都用同样格式）：
     * <pre>
     * [
     *   { "type": "function",
     *     "function": { "name": "...", "description": "...", "parameters": {...} } },
     *   ...
     * ]
     * </pre>
     */
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

    /**
     * 调用一个工具。
     * @param name           AI 选中的工具名
     * @param argumentsJson  AI 给出的 JSON 参数串（来自 tool_call.function.arguments）
     * @return 执行结果文本（成功则是工具自己的 JSON，失败则是 {"error": "..."}）
     */
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
