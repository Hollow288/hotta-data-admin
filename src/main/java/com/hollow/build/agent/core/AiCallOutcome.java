package com.hollow.build.agent.core;

import java.util.Map;

/**
 * 一次 AI HTTP 调用的完整结果，既给 Agent 主循环用（{@link #message}），
 * 也给日志服务用（其它字段）。
 *
 * <p>失败时 {@link #message} 为 null、{@link #errorMessage} 非 null；
 * Agent 应在记日志后再决定是否抛出。
 */
public record AiCallOutcome(
        Map<String, Object> message,
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
) {}
