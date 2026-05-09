package com.hollow.build.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.config.AiConfigurationProperties;
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
     * @param messages 完整对话历史（含 system / user / assistant / tool 各角色）
     * @param tools    OpenAI 兼容格式的工具数组（来自 ToolRegistry#openAiFormat）
     * @return AI 这一轮回复的 message 对象（即 choices[0].message）；其中：
     *         - 若 AI 想调工具，会含 "tool_calls" 字段；
     *         - 若 AI 给出最终答复，会含 "content" 字段。
     */
    public Map<String, Object> complete(List<Map<String, Object>> messages,
                                        List<Map<String, Object>> tools) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiConfigurationProperties.getTextModel());
        body.put("messages", messages);
        body.put("temperature", 0.2); // 工具调用希望确定性高一点
        body.put("stream", false);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        String apiKey = pickApiKey();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiConfigurationProperties.getTextUri()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        if (responseBody != null && responseBody.startsWith("[")) {
            // 错误返回常常是 JSON 数组形式
            throw new IllegalStateException("AI 接口返回错误: " + responseBody);
        }

        Map<String, Object> json = JSON.parseObject(responseBody, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI 接口返回不含 choices: " + responseBody);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return message;
    }

    private String pickApiKey() {
        List<String> keys = aiConfigurationProperties.getTextApiKey();
        return (keys == null || keys.isEmpty()) ? null : keys.get(0);
    }
}
