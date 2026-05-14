package com.hollow.build.agent.router;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.core.AgentAiClient;
import com.hollow.build.agent.core.AiCallOutcome;
import com.hollow.build.agent.entity.AgentAiCallLog;
import com.hollow.build.agent.log.AgentLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 路由器：拿到用户消息后，先用一次轻量 LLM 调用判定该派给哪个 agent。
 *
 * <p><b>关键点（v2 改造）</b>：
 * <ul>
 *   <li>不再硬编码"有哪些 agent 可选"——Spring 自动收集 {@link AbstractAgent} 所有 bean。</li>
 *   <li>路由 prompt **动态拼接**，只包含 {@link AgentSwitches} 里当前开启的 agent。
 *       被关掉的 agent，AI <b>根本不会知道它存在</b>，自然不会派单过去。</li>
 *   <li>所有 agent 都关时直接抛错，不再瞎调 LLM 浪费 token。</li>
 *   <li>只剩 1 个 agent 时短路掉 LLM，直接派发。</li>
 *   <li>LLM 给出未知 / 已关的 target 时 fallback 到第一个开启的 agent，再 warn 一笔。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentRouter {

    private static final String ROUTER_NAME = "router";

    private final AgentAiClient aiClient;
    private final AgentLogService agentLogService;
    private final AgentSwitches agentSwitches;

    /** agentName → bean。LinkedHashMap 保留注入顺序，影响 prompt 中的展示顺序。 */
    private final Map<String, AbstractAgent> agentsByName;

    public AgentRouter(AgentAiClient aiClient,
                       AgentLogService agentLogService,
                       AgentSwitches agentSwitches,
                       List<AbstractAgent> agents) {
        this.aiClient = aiClient;
        this.agentLogService = agentLogService;
        this.agentSwitches = agentSwitches;
        this.agentsByName = agents.stream().collect(Collectors.toMap(
                AbstractAgent::agentName, a -> a,
                (a, b) -> a, LinkedHashMap::new));
        log.info("AgentRouter 已识别 {} 个 agent: {}", agentsByName.size(), agentsByName.keySet());
    }

    /** 路由 + 派发的一条龙。 */
    public AbstractAgent.AgentResult route(String userMessage, String requestId, String clientIp) throws Exception {
        List<AbstractAgent> enabled = enabledAgents();

        if (enabled.isEmpty()) {
            // 所有 agent 都关了，没法做任何事 —— 早 fail，省一次 LLM 调用。
            throw new IllegalStateException("没有任何 agent 处于开启状态，请联系管理员检查开关配置");
        }

        if (enabled.size() == 1) {
            // 只剩一个候选，无需问 AI，直接派发 —— 省 token、降延迟。
            AbstractAgent only = enabled.get(0);
            log.info("仅 1 个 agent 开启，路由短路到 {} requestId={}", only.agentName(), requestId);
            return only.ask(userMessage, requestId, clientIp);
        }

        RouteDecision decision = decide(userMessage, requestId, enabled);
        log.info("路由判定 requestId={} → {} (conf={}, reason={})",
                requestId, decision.target(), decision.confidence(), decision.reason());

        AbstractAgent target = agentsByName.get(decision.target());
        if (target == null || !agentSwitches.isEnabled(target.agentName())) {
            AbstractAgent fallback = enabled.get(0);
            log.warn("路由目标 {} 不可用，fallback 到 {}", decision.target(), fallback.agentName());
            target = fallback;
        }

        return target.ask(userMessage, requestId, clientIp);
    }

    /** 仅做判定，不派发。供测试 / 调试入口直接调用。 */
    public RouteDecision decide(String userMessage, String requestId) {
        List<AbstractAgent> enabled = enabledAgents();
        if (enabled.isEmpty()) {
            return new RouteDecision(null, 0.0, "no_enabled_agents");
        }
        return decide(userMessage, requestId, enabled);
    }

    private RouteDecision decide(String userMessage, String requestId, List<AbstractAgent> enabled) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", buildRouterPrompt(enabled)),
                Map.of("role", "user", "content", userMessage)
        );

        AiCallOutcome outcome = aiClient.complete(messages, List.of()); // 不带 tools
        agentLogService.saveAiCallLog(toCallLog(outcome, requestId));

        if (outcome.errorMessage() != null) {
            String fallback = enabled.get(0).agentName();
            log.warn("路由 LLM 调用失败，fallback 到 {}: {}", fallback, outcome.errorMessage());
            return new RouteDecision(fallback, 0.0, "router_llm_error:" + outcome.errorMessage());
        }

        String content = outcome.message() == null ? "" : String.valueOf(outcome.message().get("content"));
        return parse(content, enabled);
    }

    /** 收集当前开启的 agent，按注入顺序返回。 */
    private List<AbstractAgent> enabledAgents() {
        return agentsByName.values().stream()
                .filter(a -> agentSwitches.isEnabled(a.agentName()))
                .toList();
    }

    /**
     * 动态拼路由 prompt：只把开启的 agent 列进去。
     *
     * <p>结构（按 LLM 阅读习惯排）：
     * <ol>
     *   <li>每个 agent 的"我是谁"自描述（来自 {@link AbstractAgent#routerDescription()}）</li>
     *   <li>各 agent 自报的领域术语（来自 {@link AbstractAgent#routerKeywords()}）</li>
     *   <li>全局判别元规则 —— 跨 agent 的优先级 / 冲突裁决 / 兜底策略，集中维护在此处</li>
     *   <li>few-shot 示例（来自 {@link AbstractAgent#routerExamples()}）</li>
     *   <li>输出格式约束</li>
     * </ol>
     *
     * <p>关键：**新增 agent 完全不必动 Router**。新 agent 自己实现 routerKeywords / routerExamples，
     * Router 会自动把它的描述、关键词和示例汇总进 prompt；关停时也自动消失，不会再误导 LLM
     * 把单派给一个不存在的目标。
     */
    private String buildRouterPrompt(List<AbstractAgent> enabled) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 agent 路由器。系统里目前有以下可用 agent，请根据用户问题判断该派给谁：\n\n");

        for (AbstractAgent agent : enabled) {
            sb.append("  - \"").append(agent.agentName()).append("\": ");
            // 把 routerDescription 的多行做个轻微缩进，prompt 里读起来更整齐
            String desc = agent.routerDescription().strip().replace("\n", "\n     ");
            sb.append(desc).append('\n');
        }

        // 各 agent 自报的领域术语，汇总成一段。某个 agent 没填 keywords 就不出现在这段里。
        StringBuilder keywordsBlock = new StringBuilder();
        for (AbstractAgent agent : enabled) {
            String kw = agent.routerKeywords().strip();
            if (!kw.isEmpty()) {
                keywordsBlock.append("  - \"").append(agent.agentName()).append("\": ")
                        .append(kw).append('\n');
            }
        }
        if (keywordsBlock.length() > 0) {
            sb.append("\n领域术语映射：\n").append(keywordsBlock);
        }

        sb.append('\n').append(ROUTER_META_RULES);

        // 各 agent 自报的 few-shot 示例。Router 这里自动补上 → agent 名，agent 那边不用写。
        StringBuilder examplesBlock = new StringBuilder();
        for (AbstractAgent agent : enabled) {
            for (AbstractAgent.RouterExample ex : agent.routerExamples()) {
                examplesBlock.append("  \"").append(ex.userQuery()).append("\" → ")
                        .append(agent.agentName())
                        .append("  （").append(ex.reason()).append("）\n");
            }
        }
        if (examplesBlock.length() > 0) {
            sb.append("\n参考示例：\n").append(examplesBlock);
        }

        String names = enabled.stream().map(a -> "\"" + a.agentName() + "\"")
                .collect(Collectors.joining("|"));
        sb.append('\n');
        sb.append("只输出一个 JSON 对象，不要任何额外文字、不要 markdown：\n");
        sb.append("  {\"target\":").append(names)
                .append(",\"confidence\":0~1,\"reason\":\"一句话理由\"}\n\n");
        sb.append("如果同时涉及多个，挑主要意图；拿不准就给较低 confidence 但仍要选一个。\n");
        sb.append("如果用户输入完全不属于任何一类（比如纯打字测试），也必须选一个最接近的，并给较低 confidence。\n");
        return sb.toString();
    }

    /**
     * 全局判别元规则 —— 跨 agent 的优先级 / 冲突裁决 / 兜底策略。
     *
     * <p>具体到某个 agent 的"出现什么术语就派给我"已经下放到 {@link AbstractAgent#routerKeywords()}，
     * 这里只保留**不属于任何单个 agent**的元层逻辑。
     */
    private static final String ROUTER_META_RULES = """
            判别准则（按优先级从高到低）：
              1. **按用户问题里的"对象"分类，不要看动词**。"查/找/看/搜"哪个 agent 都可能用，
                 真正决定路由的是用户在谈什么东西。
              2. 用户问题里出现上面"领域术语映射"里的术语时，按术语直接对应到 agent。
              3. 多个信号冲突时，以"出现的具体专有名词"为准（具体专有名词 > 一般动词）。
              4. 完全无法判断时，仍要选一个最接近的并给较低 confidence。
            """;

    private static RouteDecision parse(String content, List<AbstractAgent> enabled) {
        String fallback = enabled.get(0).agentName();

        if (content == null || content.isBlank()) {
            return new RouteDecision(fallback, 0.0, "empty_router_response");
        }
        // 容错：模型可能用 ```json 包起来
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
        }
        try {
            JSONObject json = JSON.parseObject(trimmed);
            String target = json.getString("target");
            Double confidence = json.getDouble("confidence");
            String reason = json.getString("reason");
            if (target == null) {
                return new RouteDecision(fallback, 0.0, "missing_target_field");
            }
            return new RouteDecision(
                    target.toLowerCase(),
                    confidence == null ? 0.5 : confidence,
                    reason == null ? "" : reason
            );
        } catch (Exception e) {
            return new RouteDecision(fallback, 0.0, "parse_error:" + content);
        }
    }

    private static AgentAiCallLog toCallLog(AiCallOutcome outcome, String requestId) {
        AgentAiCallLog log = new AgentAiCallLog();
        log.setRequestId(requestId);
        log.setAgentName(ROUTER_NAME);
        log.setIterationIndex(0);
        log.setModel(outcome.model());
        log.setRequestBody(outcome.requestBody());
        log.setResponseBody(outcome.responseBody());
        log.setHttpStatus(outcome.httpStatus());
        log.setPromptTokens(outcome.promptTokens());
        log.setCompletionTokens(outcome.completionTokens());
        log.setTotalTokens(outcome.totalTokens());
        log.setToolCallCount(outcome.toolCallCount());
        log.setFinishReason(outcome.finishReason());
        log.setDurationMs(outcome.durationMs());
        log.setErrorMessage(outcome.errorMessage());
        return log;
    }
}
