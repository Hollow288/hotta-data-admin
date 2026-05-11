package com.hollow.build.agent.config;

import lombok.Getter;

/**
 * 当某个 agent 被运营开关关闭、却收到了请求时抛出。
 *
 * <p>由 {@code GlobalExceptionHandler} 转成统一的 501 "功能未实现/未开启" 响应，
 * 业务方不用每个调用方点点 try/catch。
 */
@Getter
public class AgentDisabledException extends RuntimeException {

    private final String agentName;

    public AgentDisabledException(String agentName) {
        super("agent 已关闭: " + agentName);
        this.agentName = agentName;
    }
}
