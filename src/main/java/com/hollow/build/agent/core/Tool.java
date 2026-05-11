package com.hollow.build.agent.core;

import java.util.Map;

/**
 * 一个 Tool（工具）= Agent 可以调用的"能力"。
 *
 * 在大模型 function calling / tool use 协议里，每个 Tool 需要告诉 AI 三件事：
 *   1. name        —— 工具标识，AI 通过它"指名调用"
 *   2. description —— 这个工具是干嘛的、什么时候应该用
 *   3. parametersSchema —— 用 JSON Schema 描述参数
 *
 * 真正的执行逻辑（execute）由我们自己用 Java 写。
 *
 * Agent 的循环：
 *   AI 决定调哪个 Tool、传什么参数 → Java 执行 Tool → 把结果回传给 AI
 *   → AI 再决定下一步 → …… 直到 AI 给出最终回复。
 */
public interface Tool {

    /** AI 看到的工具名（建议用 snake_case，跟 OpenAI 等主流模型习惯一致）。 */
    String name();

    /** 工具用途描述，越清晰，AI 调用得越准。 */
    String description();

    /**
     * 参数的 JSON Schema。例如：
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "table": { "type": "string", "description": "表名" }
     *   },
     *   "required": ["table"]
     * }
     * </pre>
     */
    Map<String, Object> parametersSchema();

    /**
     * 真正执行工具。AI 给出的 JSON 参数会被解析成 Map 后传进来。
     * 返回值是 String（通常是 JSON），会作为 "工具调用结果" 回传给 AI。
     */
    String execute(Map<String, Object> arguments) throws Exception;
}
