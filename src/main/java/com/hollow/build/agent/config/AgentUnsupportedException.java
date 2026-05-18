package com.hollow.build.agent.config;

import lombok.Getter;

/**
 * Router 明确判断当前没有合适 agent 可以处理用户请求时抛出。
 */
@Getter
public class AgentUnsupportedException extends RuntimeException {

    private final String reason;

    public AgentUnsupportedException(String reason) {
        super(reason == null || reason.isBlank()
                ? "当前 agent 系统暂不支持这个问题"
                : "当前 agent 系统暂不支持这个问题: " + reason);
        this.reason = reason;
    }
}
