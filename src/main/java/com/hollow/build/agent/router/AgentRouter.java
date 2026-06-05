package com.hollow.build.agent.router;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.config.AgentUnsupportedException;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.entity.AgentAiCallLog;
import com.hollow.build.agent.log.AgentLogService;
import com.hollow.build.ai.client.JsonValues;
import com.hollow.build.ai.client.openai.ChatMessages;
import com.hollow.build.ai.client.openai.ChatRequest;
import com.hollow.build.ai.client.openai.ChatResult;
import com.hollow.build.ai.client.openai.ChatTools;
import com.hollow.build.ai.client.openai.OpenAiChatClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 路由器：拿到用户消息后，用一次路由专用 function calling 判定该派给哪个 agent。
 *
 * <p>Router 这一轮只给 AI 暴露 {@code route_to_xxx} 这类虚拟工具，不暴露任何业务工具。
 * 业务工具仍然只会在目标 {@link AbstractAgent} 的主循环里发送给 AI。
 */
@Slf4j
@Component
public class AgentRouter {

    private static final String ROUTER_NAME = "router";
    private static final String ROUTE_PREFIX = "route_to_";
    public static final String UNSUPPORTED_TARGET = "unsupported";

    private final OpenAiChatClient openAiChatClient;
    private final AgentLogService agentLogService;
    private final AgentSwitches agentSwitches;

    /** agentName -> bean。LinkedHashMap 保留注入顺序，影响 prompt 中的展示顺序。 */
    private final Map<String, AbstractAgent> agentsByName;

    public AgentRouter(OpenAiChatClient openAiChatClient,
                       AgentLogService agentLogService,
                       AgentSwitches agentSwitches,
                       List<AbstractAgent> agents) {
        this.openAiChatClient = openAiChatClient;
        this.agentLogService = agentLogService;
        this.agentSwitches = agentSwitches;
        this.agentsByName = agents.stream().collect(Collectors.toMap(
                AbstractAgent::agentName, a -> a,
                (a, b) -> a, LinkedHashMap::new));
        log.info("AgentRouter 已识别 {} 个 agent: {}", agentsByName.size(), agentsByName.keySet());
    }

    /** 路由 + 派发。 */
    public AbstractAgent.AgentResult route(String userMessage, String requestId, String clientIp) throws Exception {
        List<AbstractAgent> enabled = enabledAgents();

        if (enabled.isEmpty()) {
            throw new AgentUnsupportedException("没有任何 agent 处于开启状态");
        }

        RouteDecision decision = decide(userMessage, requestId, enabled);
        log.info("路由判定 requestId={} -> {} (conf={}, reason={})",
                requestId, decision.target(), decision.confidence(), decision.reason());

        if (UNSUPPORTED_TARGET.equals(decision.target())) {
            throw new AgentUnsupportedException(decision.reason());
        }

        AbstractAgent target = agentsByName.get(decision.target());
        if (target == null || !agentSwitches.isEnabled(target.agentName())) {
            throw new IllegalStateException("路由目标不可用: " + decision.target());
        }

        return target.ask(userMessage, requestId, clientIp);
    }

    /** 仅做判定，不派发。供测试 / 调试入口直接调用。 */
    public RouteDecision decide(String userMessage, String requestId) {
        List<AbstractAgent> enabled = enabledAgents();
        if (enabled.isEmpty()) {
            return new RouteDecision(UNSUPPORTED_TARGET, 1.0, "没有任何 agent 处于开启状态");
        }
        try {
            return decide(userMessage, requestId, enabled);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            return new RouteDecision(UNSUPPORTED_TARGET, 0.0, "router_error:" + reason);
        }
    }

    private RouteDecision decide(String userMessage, String requestId, List<AbstractAgent> enabled) throws Exception {
        List<Map<String, Object>> messages = List.of(
                ChatMessages.system(buildRouterPrompt(enabled)),
                ChatMessages.user(userMessage)
        );

        ChatResult outcome = openAiChatClient.complete(
                ChatRequest.builder()
                        .messages(messages)
                        .tools(buildRouteTools(enabled))
                        .toolChoice("required")
                        .temperature(0.2)
                        .build());
        agentLogService.saveAiCallLog(AgentAiCallLog.from(outcome, requestId, ROUTER_NAME, 0));

        if (outcome.errorMessage() != null) {
            throw new IllegalStateException("路由 LLM 调用失败: " + outcome.errorMessage());
        }

        return parseToolCall(outcome.message(), enabled);
    }

    /** 收集当前开启的 agent，按注入顺序返回。 */
    private List<AbstractAgent> enabledAgents() {
        return agentsByName.values().stream()
                .filter(a -> agentSwitches.isEnabled(a.agentName()))
                .toList();
    }

    /**
     * 动态拼路由 prompt：只给 Router 放元规则和 few-shot。
     *
     * <p>每个 agent 的描述 / 关键词会进入各自的 {@code route_to_xxx} tool description；
     * few-shot 继续留在 system prompt 里，因为 function calling 只约束输出形式，不替代路由判断知识。
     */
    private String buildRouterPrompt(List<AbstractAgent> enabled) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 agent 路由器。请根据用户问题调用最合适的 route_to_xxx 工具。\n");
        sb.append("不要直接回答用户；不要输出普通文本；必须且只能调用一个路由工具。\n\n");

        String names = enabled.stream()
                .map(AbstractAgent::agentName)
                .collect(Collectors.joining(" / "));
        sb.append("当前可用业务 agent：").append(names).append("。\n");
        sb.append("如果没有任何业务 agent 适合处理用户问题，调用 route_to_unsupported。\n\n");
        sb.append(ROUTER_META_RULES);

        StringBuilder examplesBlock = new StringBuilder();
        for (AbstractAgent agent : enabled) {
            for (AbstractAgent.RouterExample ex : agent.routerExamples()) {
                examplesBlock.append("  \"").append(ex.userQuery()).append("\" -> ")
                        .append(ROUTE_PREFIX).append(agent.agentName())
                        .append("  （").append(ex.reason()).append("）\n");
            }
        }
        examplesBlock.append("  \"帮我生成一张图片\" -> ")
                .append(ROUTE_PREFIX).append(UNSUPPORTED_TARGET)
                .append("  （当前没有图片生成类 agent）\n");
        examplesBlock.append("  \"今天天气怎么样\" -> ")
                .append(ROUTE_PREFIX).append(UNSUPPORTED_TARGET)
                .append("  （当前没有天气查询类 agent）\n");

        sb.append("\n参考示例：\n").append(examplesBlock);
        return sb.toString();
    }

    /**
     * 全局判别元规则 —— 跨 agent 的优先级 / 冲突裁决 / 兜底策略。
     */
    private static final String ROUTER_META_RULES = """
            判别准则（按优先级从高到低）：
              1. 按用户问题里的"对象"分类，不要看动词。"查/找/看/搜"哪个 agent 都可能用，
                 真正决定路由的是用户在谈什么东西。
              2. 用户问题里出现 route_to_xxx 工具描述中的领域术语时，优先按术语对应到 agent。
              3. 多个信号冲突时，以"出现的具体专有名词"为准（具体专有名词 > 一般动词）。
              4. 对"查 X / X 是什么 / X 是哪个 / 帮我查查 X"这类识别型提问：即便你
                 不能判断 X 属于哪个 agent 的领域（X 可能是一个看起来很普通的名词、
                 现实事物名、人名、动物名、菜名等），也**不要直接 route_to_unsupported**。
                 业务 agent 内部会自己做别名 / 表名 / 词典搜索兜底，路由层不要替它们
                 提前否决——应从已开启的业务 agent 中挑一个语义最贴近"把名词识别成
                 已知实体"任务的派过去（通常是别名 / 词典 / 字典类 agent）。
              5. 当且仅当用户问题**明显**属于当前所有 agent 都不覆盖的领域时
                 （图片生成、天气查询、纯闲聊、需要外部实时信息、数学计算、翻译等），
                 才调用 route_to_unsupported。"我不认识这个名词"不是 unsupported 的
                 理由——让业务 agent 去试。
            """;

    private List<Map<String, Object>> buildRouteTools(List<AbstractAgent> enabled) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (AbstractAgent agent : enabled) {
            tools.add(routeTool(agent));
        }
        tools.add(unsupportedRouteTool());
        return tools;
    }

    private static Map<String, Object> routeTool(AbstractAgent agent) {
        StringBuilder desc = new StringBuilder();
        desc.append("当用户请求应该交给 \"").append(agent.agentName()).append("\" agent 处理时调用。\n");
        desc.append(agent.routerDescription().strip());

        String keywords = agent.routerKeywords().strip();
        if (!keywords.isEmpty()) {
            desc.append("\n领域术语：").append(keywords);
        }

        return tool(ROUTE_PREFIX + agent.agentName(), desc.toString());
    }

    private static Map<String, Object> unsupportedRouteTool() {
        return tool(ROUTE_PREFIX + UNSUPPORTED_TARGET,
                "当前没有任何可用业务 agent 适合处理用户请求时调用。"
                        + "例如图片生成、天气查询、闲聊、外部实时信息查询等当前系统未提供的能力。");
    }

    private static Map<String, Object> tool(String name, String description) {
        return ChatTools.function(name, description, routeParametersSchema());
    }

    private static Map<String, Object> routeParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "confidence", Map.of(
                                "type", "number",
                                "description", "0 到 1 的路由置信度"
                        ),
                        "reason", Map.of(
                                "type", "string",
                                "description", "一句话说明为什么调用这个路由工具"
                        )
                ),
                "required", List.of("confidence", "reason")
        );
    }

    @SuppressWarnings("unchecked")
    private static RouteDecision parseToolCall(Map<String, Object> message, List<AbstractAgent> enabled) {
        if (message == null) {
            throw new IllegalStateException("路由响应缺少 message");
        }

        Object toolCallsObj = message.get("tool_calls");
        if (!(toolCallsObj instanceof List<?> toolCalls) || toolCalls.isEmpty()) {
            Object content = message.get("content");
            throw new IllegalStateException("路由响应没有 tool_calls: " + content);
        }

        Object first = toolCalls.get(0);
        if (!(first instanceof Map<?, ?> call)) {
            throw new IllegalStateException("路由 tool_call 格式异常: " + JSON.toJSONString(first));
        }

        Object functionObj = call.get("function");
        if (!(functionObj instanceof Map<?, ?> function)) {
            throw new IllegalStateException("路由 tool_call 缺少 function: " + JSON.toJSONString(first));
        }

        String functionName = JsonValues.asString(function.get("name"));
        String target = parseTarget(functionName);
        if (!UNSUPPORTED_TARGET.equals(target) && enabled.stream().noneMatch(a -> a.agentName().equals(target))) {
            throw new IllegalStateException("路由目标不在当前开启列表中: " + target);
        }

        String arguments = JsonValues.asString(function.get("arguments"));
        JSONObject args = arguments == null || arguments.isBlank()
                ? new JSONObject()
                : JSON.parseObject(arguments);

        Double confidence = args.getDouble("confidence");
        String reason = args.getString("reason");
        return new RouteDecision(
                target,
                confidence == null ? 0.5 : confidence,
                reason == null ? "" : reason
        );
    }

    private static String parseTarget(String functionName) {
        if (functionName == null || !functionName.startsWith(ROUTE_PREFIX)) {
            throw new IllegalStateException("未知路由工具: " + functionName);
        }
        String target = functionName.substring(ROUTE_PREFIX.length());
        if (target.isBlank()) {
            throw new IllegalStateException("路由工具缺少目标: " + functionName);
        }
        return target;
    }

}
