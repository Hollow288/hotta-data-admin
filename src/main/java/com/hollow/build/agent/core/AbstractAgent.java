package com.hollow.build.agent.core;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.config.AgentDisabledException;
import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.entity.AgentAiCallLog;
import com.hollow.build.agent.entity.AgentRequestLog;
import com.hollow.build.agent.log.AgentLogService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 主循环的通用实现。
 *
 * <p>"Agent = 大模型 + 一组工具 + 让 AI 反复决策直到完成任务的循环"。这个循环本身
 * 跟具体业务无关 —— 不同 agent 之间的差异只在三处：
 * <ol>
 *   <li>{@link #systemPrompt()} —— 它是个什么角色、典型流程是什么</li>
 *   <li>{@link #toolRegistry} —— 它能调哪些工具</li>
 *   <li>{@link #agentName()} —— 用于日志区分</li>
 * </ol>
 * 所以把循环放在基类、子类只填这三块即可。
 */
public abstract class AbstractAgent {

    protected final AgentAiClient aiClient;
    protected final ToolRegistry toolRegistry;
    protected final AgentProperties agentProperties;
    protected final AgentLogService agentLogService;
    protected final AgentSwitches agentSwitches;

    protected AbstractAgent(AgentAiClient aiClient,
                            ToolRegistry toolRegistry,
                            AgentProperties agentProperties,
                            AgentLogService agentLogService,
                            AgentSwitches agentSwitches) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
        this.agentLogService = agentLogService;
        this.agentSwitches = agentSwitches;
    }

    /** 子类用来描述自己是谁、典型工作流。 */
    protected abstract String systemPrompt();

    /** 用于日志中区分是哪个 agent。Router 也按它做 Map 键。 */
    public abstract String agentName();

    /**
     * 给 Router 看的"我是谁、什么场景该派给我"。
     *
     * <p>{@link com.hollow.build.agent.router.AgentRouter} 会**只拼当前开启**的 agent
     * 的描述到路由 prompt 里——所以被关闭的 agent，AI 根本看不到它存在，自然不会派单过来。
     * 这跟 {@link #systemPrompt()} 是两种不同视角：前者是"对 router 的自我介绍"，
     * 后者是"对自己工作模式的说明"。
     */
    public abstract String routerDescription();

    /**
     * 该 agent 独有的"领域术语 / 关键词"列表，给 Router 做判别用。
     *
     * <p>Router 会把所有开启 agent 的 keywords 汇总成"领域术语映射"段塞进路由 prompt：
     * <pre>
     *   领域术语映射：
     *     - "database": 出现表名（user / blog_posts ...）、字段、SQL、...
     *     - "alias":    出现游戏物品类型（武器 / 意志 / 源器）...
     * </pre>
     * 这样新增 / 关停 agent 时，关键词清单跟着 agent 走，Router 完全不用动。
     *
     * <p>默认空串表示这个 agent 没有专属术语 —— 完全依赖 {@link #routerDescription()} 兜底。
     */
    public String routerKeywords() {
        return "";
    }

    /**
     * 该 agent 的代表性 few-shot 示例。Router 会把所有开启 agent 的示例汇总后塞进路由 prompt。
     *
     * <p>跟 {@link #routerKeywords()} 一样：示例随 agent 走，关停一个 agent 它的示例也跟着消失，
     * 不会再误导 LLM 把单子派去一个不存在的目标。
     *
     * <p>每条示例形如：用户原话 + "为什么该派给我"的简短理由。Router 在格式化时会自动补上
     * agent 名字，所以这里**不要重复写** "→ database" 之类。
     */
    public List<RouterExample> routerExamples() {
        return List.of();
    }

    /** Router few-shot 示例的数据载体。 */
    public record RouterExample(String userQuery, String reason) {}

    /**
     * 把模型最终回复转换为给客户端使用的结构化数据。默认无结构化数据。
     *
     * <p>例如 AliasAgent 会把 {@code {"type":"武器","value":"赤风"}} 解析成对象；
     * DatabaseAgent 则保持自然语言文本，结构化数据为 null。
     */
    protected Object answerData(String reply) {
        return null;
    }

    /** 给客户端展示的稳定文本。默认直接使用模型最终回复。 */
    protected String answerText(String reply, Object answerData) {
        return reply == null ? "" : reply;
    }

    /** 兼容入口：自己生成 requestId，不带 clientIp。 */
    public AgentResult ask(String userMessage) throws Exception {
        return ask(userMessage, UUID.randomUUID().toString().replace("-", ""), null);
    }

    public AgentResult ask(String userMessage, String requestId, String clientIp) throws Exception {

        // 入口拦截：开关一关，无论 router 派发还是直连入口都走不通，语义统一。
        if (!agentSwitches.isEnabled(agentName())) {
            throw new AgentDisabledException(agentName());
        }

        long askStart = System.currentTimeMillis();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> traceEntries = new ArrayList<>();
        List<String> traceForUi = new ArrayList<>();

        int iterations = 0;
        int totalToolCalls = 0;
        int totalTokens = 0;
        String model = null;
        String status = "MAX_ITERATIONS";
        String reply = null;
        String errorMessage = null;

        try {
            for (int i = 0; i < agentProperties.getMaxIterations(); i++) {
                iterations = i + 1;

                AiCallOutcome outcome = aiClient.complete(messages, toolRegistry.openAiFormat());

                model = outcome.model();
                if (outcome.totalTokens() != null) {
                    totalTokens += outcome.totalTokens();
                }

                agentLogService.saveAiCallLog(toCallLog(outcome, requestId, agentName(), i));

                if (outcome.errorMessage() != null) {
                    status = "ERROR";
                    errorMessage = outcome.errorMessage();
                    throw new IllegalStateException(outcome.errorMessage());
                }

                Map<String, Object> aiMessage = outcome.message();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls =
                        (List<Map<String, Object>>) aiMessage.get("tool_calls");

                if (toolCalls == null || toolCalls.isEmpty()) {
                    Object content = aiMessage.get("content");
                    reply = content == null ? "" : content.toString();
                    status = "SUCCESS";
                    return buildResult(reply, traceForUi);
                }

                messages.add(sanitizeAssistantMessage(aiMessage));

                for (Map<String, Object> call : toolCalls) {
                    String callId = (String) call.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> function = (Map<String, Object>) call.get("function");
                    String fnName = (String) function.get("name");
                    String fnArgs = (String) function.get("arguments");

                    long toolStart = System.currentTimeMillis();
                    String result = toolRegistry.invoke(fnName, fnArgs);
                    long toolMs = System.currentTimeMillis() - toolStart;

                    totalToolCalls++;

                    Map<String, Object> traceEntry = new LinkedHashMap<>();
                    traceEntry.put("iteration", i);
                    traceEntry.put("tool", fnName);
                    traceEntry.put("args", fnArgs);
                    traceEntry.put("result", result);
                    traceEntry.put("ms", toolMs);
                    traceEntries.add(traceEntry);

                    traceForUi.add(fnName + "(" + (fnArgs == null ? "" : fnArgs) + ") -> " + result);

                    Map<String, Object> toolMessage = new HashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", callId);
                    toolMessage.put("name", fnName);
                    toolMessage.put("content", result);
                    messages.add(toolMessage);
                }
            }

            reply = "达到最大迭代次数（" + agentProperties.getMaxIterations() + "）仍未结束，已停止。";
            return buildResult(reply, traceForUi);

        } catch (Exception e) {
            if (errorMessage == null) {
                errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
                status = "ERROR";
            }
            throw e;
        } finally {
            AgentRequestLog logEntity = new AgentRequestLog();
            logEntity.setRequestId(requestId);
            logEntity.setAgentName(agentName());
            logEntity.setUserMessage(userMessage);
            logEntity.setModel(model);
            logEntity.setReply(reply);
            logEntity.setTrace(traceEntries.isEmpty() ? null : JSON.toJSONString(traceEntries));
            logEntity.setIterations(iterations);
            logEntity.setToolCallCount(totalToolCalls);
            logEntity.setTotalTokens(totalTokens == 0 ? null : totalTokens);
            logEntity.setStatus(status);
            logEntity.setErrorMessage(errorMessage);
            logEntity.setDurationMs(System.currentTimeMillis() - askStart);
            logEntity.setClientIp(clientIp);
            agentLogService.saveRequestLog(logEntity);
        }
    }

    private AgentResult buildResult(String reply, List<String> traceForUi) {
        Object data = answerData(reply);
        return new AgentResult(agentName(), answerText(reply, data), data, traceForUi);
    }

    private static AgentAiCallLog toCallLog(AiCallOutcome outcome, String requestId,
                                            String agentName, int iterationIndex) {
        AgentAiCallLog log = new AgentAiCallLog();
        log.setRequestId(requestId);
        log.setAgentName(agentName);
        log.setIterationIndex(iterationIndex);
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

    private static Map<String, Object> sanitizeAssistantMessage(Map<String, Object> aiMessage) {
        Map<String, Object> copy = new HashMap<>(aiMessage);
        copy.putIfAbsent("role", "assistant");
        if (copy.get("content") == null) {
            copy.put("content", "");
        }
        return copy;
    }

    /** Agent 的最终输出：面向客户端的答案 + 可选调试轨迹。 */
    public record AgentResult(String agentName, String answerText, Object answerData, List<String> debugTrace) {}
}
