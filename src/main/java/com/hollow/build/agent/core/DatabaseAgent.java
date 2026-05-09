package com.hollow.build.agent.core;

import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent = 大模型 + 一组工具 + 一个让 AI 反复决策直到完成任务的循环。
 *
 * <p>这里实现的是最经典的 ReAct / function-calling 风格循环：
 * <pre>
 *   1. 把系统提示 + 用户问题塞进 messages
 *   2. 带着 messages 和工具列表调一次 AI
 *   3. 如果 AI 返回的 message 里有 tool_calls：
 *        a. 把这条 assistant message 原样追加回 messages（让 AI 看到自己上一轮请求过哪些工具）
 *        b. 依次执行这些工具，每个结果作为 role=tool 的消息追加回 messages
 *        c. 回到第 2 步
 *      否则（AI 直接给出 content）：把 content 作为最终答复返回
 *   4. 为了防止死循环，最多跑 maxIterations 轮
 * </pre>
 *
 * <p>这就是大多数"AI Agent 框架"的最小内核——LangChain、Spring AI 的 Tool Calling、
 * Anthropic 官方 SDK 都是在这个循环之上加了花活。
 */
@Service
public class DatabaseAgent {

    private final AgentAiClient aiClient;
    private final ToolRegistry toolRegistry;
    private final AgentProperties agentProperties;

    public DatabaseAgent(AgentAiClient aiClient, ToolRegistry toolRegistry, AgentProperties agentProperties) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
    }

    public AgentResult ask(String userMessage) throws Exception {

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        messages.add(Map.of("role", "user", "content", userMessage));

        List<String> trace = new ArrayList<>();

        for (int i = 0; i < agentProperties.getMaxIterations(); i++) {

            Map<String, Object> aiMessage = aiClient.complete(messages, toolRegistry.openAiFormat());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) aiMessage.get("tool_calls");

            if (toolCalls == null || toolCalls.isEmpty()) {
                // ===== 终止：AI 给出了最终答案 =====
                Object content = aiMessage.get("content");
                return new AgentResult(content == null ? "" : content.toString(), trace);
            }

            // ===== 继续：AI 让我们调几个工具 =====

            // 1) 先把 assistant 这条消息塞回去（必须保留 tool_calls，下一轮 AI 才知道前因后果）
            messages.add(sanitizeAssistantMessage(aiMessage));

            // 2) 依次执行工具，每个结果作为 role=tool 的消息追加回去
            for (Map<String, Object> call : toolCalls) {
                String callId = (String) call.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) call.get("function");
                String fnName = (String) function.get("name");
                String fnArgs = (String) function.get("arguments");

                String result = toolRegistry.invoke(fnName, fnArgs);
                trace.add(fnName + "(" + (fnArgs == null ? "" : fnArgs) + ") -> " + result);

                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", callId);
                toolMessage.put("name", fnName);
                toolMessage.put("content", result);
                messages.add(toolMessage);
            }
        }

        return new AgentResult(
                "达到最大迭代次数（" + agentProperties.getMaxIterations() + "）仍未结束，已停止。",
                trace
        );
    }

    /**
     * 把 AI 返回的 assistant 消息原样塞回 messages，但是把 null content 替换成 ""，
     * 因为部分网关对 null content 不友好。
     */
    private Map<String, Object> sanitizeAssistantMessage(Map<String, Object> aiMessage) {
        Map<String, Object> copy = new HashMap<>(aiMessage);
        copy.putIfAbsent("role", "assistant");
        if (copy.get("content") == null) {
            copy.put("content", "");
        }
        return copy;
    }

    private String systemPrompt() {
        return """
                你是一个数据库查询助手。你只能通过提供的工具来访问数据库，不能编造数据。
                典型流程：
                  1. 先用 list_tables 看看能查哪些表；
                  2. 用 describe_table 了解目标表的字段；
                  3. 用 query_table 拿到数据（必要时加 whereField/whereValue 过滤）；
                  4. 用自然语言把结果总结给用户，并附上你查到的关键字段。
                如果用户的问题不能通过这些工具回答，请直接说明无法处理，不要瞎编。
                """;
    }

    /** Agent 的最终输出：AI 的回复 + 这一轮里调过的工具轨迹（便于排查）。 */
    public record AgentResult(String reply, List<String> trace) {}
}
