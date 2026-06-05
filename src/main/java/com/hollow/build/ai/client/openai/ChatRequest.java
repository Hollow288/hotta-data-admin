package com.hollow.build.ai.client.openai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 一次 OpenAI 标准 Chat Completions 调用的入参。
 *
 * <p>只描述「这次想对模型说什么、用什么参数」，不关心 key、地址、模型名等传输细节——
 * 那些由 {@link OpenAiChatClient} 从配置里取。
 */
@Getter
@Builder
public class ChatRequest {

    /** 完整对话历史（OpenAI messages 数组）。 */
    private final List<Map<String, Object>> messages;

    /** 可选：覆盖本次调用使用的模型；为空（默认）则用配置的文本模型（{@code com.hollow.ai.text-model}）。 */
    private final String model;

    /** 采样温度，默认 1.0。 */
    @Builder.Default
    private final double temperature = 1.0;

    /** 可选的工具列表（function calling）；为空则不下发。 */
    private final List<Map<String, Object>> tools;

    /** 可选的 tool_choice（如 auto / required）；为空则不下发。 */
    private final String toolChoice;

    /** 本次请求的读超时（秒），默认 60。 */
    @Builder.Default
    private final int timeoutSeconds = 60;
}
