package com.hollow.build.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带工具调用支持的大模型客户端。
 *
 * <p>跟项目里 {@code AiChatServiceImpl} 的差别：
 * <ul>
 *   <li>请求体里多了 "tools" 字段（function calling 协议）</li>
 *   <li>解析响应时关心的不只是 content，还有 tool_calls</li>
 *   <li>返回 {@link AiCallOutcome}，把请求/响应/tokens 等元数据一并带出去给日志层</li>
 * </ul>
 *
 * <p>本实现复用了项目原本的文本模型配置（com.hollow.ai.text-*），并且简化了 API key
 * 选取逻辑（直接取第一把）。生产环境可以参考 AiChatServiceImpl 加上 Redis 限流标记。
 */
@Component
public class AgentAiClient {

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;

    public AgentAiClient(AiConfigurationProperties aiConfigurationProperties) {
        this.aiConfigurationProperties = aiConfigurationProperties;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(40))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);

        if (aiConfigurationProperties.isProxyEnabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    aiConfigurationProperties.getProxyAddress(),
                    aiConfigurationProperties.getProxyPort()
            )));
        }

        this.httpClient = builder.build();
    }

    /**
     * 发送一次 chat completion 请求，并把可用工具列表也一起告诉 AI。
     *
     * <p>本方法不抛出异常 —— 网络异常、解析异常都会被 catch 并写入
     * {@link AiCallOutcome#errorMessage()}。调用方（DatabaseAgent）应当
     * 先把 outcome 写日志、再根据 errorMessage 决定是否中断主循环。
     *
     * @param messages 完整对话历史
     * @param tools    OpenAI 兼容格式的工具数组
     * @return 完整调用结果，包含 AI 这一轮回复的 message 以及供日志使用的元数据
     */
    public AiCallOutcome complete(List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools) {
        return complete(messages, tools, "auto");
    }

    /**
     * 发送一次 chat completion 请求，并允许调用方指定 tool_choice。
     *
     * <p>普通业务 Agent 使用 {@code auto}，让模型自行决定是否继续调工具；
     * Router 使用 {@code required}，强制模型在 route_to_xxx 工具中选一个，避免自由文本 JSON。
     */
    public AiCallOutcome complete(List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools,
                                  String toolChoice) {

        String model = aiConfigurationProperties.getTextModel();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.2); // 工具调用希望确定性高一点
        body.put("stream", false);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            if (toolChoice != null && !toolChoice.isBlank()) {
                body.put("tool_choice", toolChoice);
            }
        }

        String requestBody = JSON.toJSONString(body);
        long start = System.currentTimeMillis();

        try {
            String apiKey = pickApiKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfigurationProperties.getTextUri()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;
            String responseBody = response.body();
            int httpStatus = response.statusCode();

            if (responseBody != null && responseBody.startsWith("[")) {
                // 错误返回常常是 JSON 数组形式
                return failure(model, requestBody, responseBody, httpStatus, durationMs,
                        "AI 接口返回错误: " + responseBody);
            }

            Map<String, Object> json = JSON.parseObject(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
            if (choices == null || choices.isEmpty()) {
                return failure(model, requestBody, responseBody, httpStatus, durationMs,
                        "AI 接口返回不含 choices: " + responseBody);
            }
            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            String finishReason = asString(choice.get("finish_reason"));
            int toolCallCount = countToolCalls(message);

            Integer prompt = null, completion = null, total = null;
            Object usageObj = json.get("usage");
            if (usageObj instanceof Map<?, ?> usage) {
                prompt = asInt(usage.get("prompt_tokens"));
                completion = asInt(usage.get("completion_tokens"));
                total = asInt(usage.get("total_tokens"));
            }

            return new AiCallOutcome(message, model, requestBody, responseBody,
                    httpStatus, durationMs,
                    prompt, completion, total,
                    finishReason, toolCallCount, null);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            return failure(model, requestBody, null, null, durationMs,
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static AiCallOutcome failure(String model, String requestBody, String responseBody,
                                         Integer httpStatus, long durationMs, String errorMessage) {
        return new AiCallOutcome(null, model, requestBody, responseBody,
                httpStatus, durationMs,
                null, null, null,
                null, 0, errorMessage);
    }

    @SuppressWarnings("unchecked")
    private static int countToolCalls(Map<String, Object> message) {
        if (message == null) {
            return 0;
        }
        Object toolCalls = message.get("tool_calls");
        if (toolCalls instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private String pickApiKey() {
        List<String> keys = aiConfigurationProperties.getTextApiKey();
        return (keys == null || keys.isEmpty()) ? null : keys.get(0);
    }
}
