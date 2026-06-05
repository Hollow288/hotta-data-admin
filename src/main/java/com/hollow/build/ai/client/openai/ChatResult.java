package com.hollow.build.ai.client.openai;

import java.util.Map;

/**
 * 一次 Chat Completions 调用的完整结果。
 *
 * <p>既给业务层用（{@link #content}）、给 Agent 主循环用（{@link #message} 里的 tool_calls），
 * 也给日志层用（model / tokens / 原始报文等）。
 *
 * <p>失败时 {@link #message} 与 {@link #content} 可能为 null，{@link #errorMessage} 非空；
 * {@link OpenAiChatClient#complete} 不抛异常，调用方根据 {@link #errorMessage} 自行决定如何处理。
 */
public record ChatResult(
        Map<String, Object> message,
        String content,
        String model,
        String requestBody,
        String responseBody,
        Integer httpStatus,
        long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        int toolCallCount,
        String errorMessage
) {
    /** 是否调用失败。 */
    public boolean isError() {
        return errorMessage != null;
    }
}
