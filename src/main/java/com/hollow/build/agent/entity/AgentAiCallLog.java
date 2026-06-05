package com.hollow.build.agent.entity;

import com.hollow.build.ai.client.openai.ChatResult;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 单次 AI HTTP 调用日志（调用粒度）。
 * Agent 主循环里每跑一轮 chat/completions 就插一行。
 */
@Data
public class AgentAiCallLog implements Serializable {

    /** 主键 */
    private Long id;

    /** 主表 agent_request_log.id（异步写入，可能为 null） */
    private Long requestLogId;

    /** 请求唯一 ID（与主表关联） */
    private String requestId;

    /** 产生本条调用的 agent 名（router / database / alias） */
    private String agentName;

    /** 在主循环里的轮次（从 0 开始） */
    private Integer iterationIndex;

    /** 请求使用的模型 */
    private String model;

    /** 完整请求体 JSON */
    private String requestBody;

    /** 完整响应体 JSON */
    private String responseBody;

    /** HTTP 状态码 */
    private Integer httpStatus;

    /** prompt_tokens */
    private Integer promptTokens;

    /** completion_tokens */
    private Integer completionTokens;

    /** total_tokens */
    private Integer totalTokens;

    /** 本轮 AI 触发了几个 tool_call */
    private Integer toolCallCount;

    /** finish_reason */
    private String finishReason;

    /** HTTP 调用耗时（毫秒） */
    private Long durationMs;

    /** 失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由一次 {@link ChatResult} 构造调用日志。
     *
     * <p>{@code AbstractAgent} 主循环与 {@code AgentRouter} 原本各抄一份相同的字段拷贝，
     * 收敛到这个工厂；router 的轮次固定传 0。
     */
    public static AgentAiCallLog from(ChatResult result, String requestId, String agentName, int iterationIndex) {
        AgentAiCallLog log = new AgentAiCallLog();
        log.setRequestId(requestId);
        log.setAgentName(agentName);
        log.setIterationIndex(iterationIndex);
        log.setModel(result.model());
        log.setRequestBody(result.requestBody());
        log.setResponseBody(result.responseBody());
        log.setHttpStatus(result.httpStatus());
        log.setPromptTokens(result.promptTokens());
        log.setCompletionTokens(result.completionTokens());
        log.setTotalTokens(result.totalTokens());
        log.setToolCallCount(result.toolCallCount());
        log.setFinishReason(result.finishReason());
        log.setDurationMs(result.durationMs());
        log.setErrorMessage(result.errorMessage());
        return log;
    }
}
