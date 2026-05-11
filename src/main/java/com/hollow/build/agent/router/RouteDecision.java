package com.hollow.build.agent.router;

/**
 * 路由判定结果。
 * @param target     选中的 agent 名（与 AbstractAgent#agentName() 对齐）
 * @param confidence 置信度 0~1（仅供日志/排查用）
 * @param reason     模型给出的原因（一句话）
 */
public record RouteDecision(String target, double confidence, String reason) {}
