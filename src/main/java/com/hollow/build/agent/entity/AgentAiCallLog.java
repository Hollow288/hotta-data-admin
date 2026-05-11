package com.hollow.build.agent.entity;

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
}
