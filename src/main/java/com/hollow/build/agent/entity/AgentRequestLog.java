package com.hollow.build.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 请求日志（业务粒度）。
 * 一次 /agent/ask 调用 = 一行。
 */
@Data
public class AgentRequestLog implements Serializable {

    /** 主键 */
    private Long id;

    /** 请求唯一 ID（UUID），与 {@link AgentAiCallLog#getRequestId()} 关联 */
    private String requestId;

    /** 处理本次请求的 agent 名（database / alias / ...） */
    private String agentName;

    /** 用户原始输入 */
    private String userMessage;

    /** 使用的模型名 */
    private String model;

    /** AI 最终自然语言回复 */
    private String reply;

    /** 工具调用轨迹（JSON 数组） */
    private String trace;

    /** Agent 主循环实际迭代次数 */
    private Integer iterations;

    /** 总工具调用次数 */
    private Integer toolCallCount;

    /** 本次 ask 累计消耗 token */
    private Integer totalTokens;

    /** 状态：SUCCESS / ERROR / MAX_ITERATIONS */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 总耗时（毫秒） */
    private Long durationMs;

    /** 客户端 IP */
    private String clientIp;

    /** 创建时间 */
    private LocalDateTime createTime;
}
