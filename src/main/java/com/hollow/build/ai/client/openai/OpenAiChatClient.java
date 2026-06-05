package com.hollow.build.ai.client.openai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ai.client.AiApiKeyProvider;
import com.hollow.build.ai.client.JsonValues;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 标准 Chat Completions 接口客户端。
 *
 * <p>统一封装「选 key → 拼请求体 → 发请求 → 判错/限流标记 → 解析响应/提取内容」这套流程，
 * 支持普通对话、视觉识图（图片以 multipart content 传入 messages）、function calling（tools）
 * 以及 SSE 流式。业务层只负责拼 messages 与处理结果，不再各写一遍传输与解析。
 *
 * <p>使用项目既有的文本模型配置（{@code com.hollow.ai.text-*}）。
 */
@Component
public class OpenAiChatClient {

    private static final String NO_KEY_MESSAGE = "无可用 AI API Key（未配置或均已被限流）";

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final AiApiKeyProvider apiKeyProvider;

    public OpenAiChatClient(HttpClient aiHttpClient,
                            AiConfigurationProperties aiConfigurationProperties,
                            AiApiKeyProvider apiKeyProvider) {
        this.httpClient = aiHttpClient;
        this.aiConfigurationProperties = aiConfigurationProperties;
        this.apiKeyProvider = apiKeyProvider;
    }

    /**
     * 发送一次（非流式）Chat Completions 请求。
     *
     * <p>不抛异常：网络异常、上游报错、无可用 key 都会落到 {@link ChatResult#errorMessage()}。
     */
    public ChatResult complete(ChatRequest request) {
        String model = resolveModel(request);
        String requestBody = JSON.toJSONString(buildBody(request, model, false));
        long start = System.currentTimeMillis();

        String apiKey = apiKeyProvider.pickTextKey();
        if (apiKey == null) {
            return failure(model, requestBody, null, null, elapsed(start), NO_KEY_MESSAGE);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    buildHttpRequest(requestBody, apiKey, request.getTimeoutSeconds()),
                    HttpResponse.BodyHandlers.ofString());

            long durationMs = elapsed(start);
            String responseBody = response.body();
            int httpStatus = response.statusCode();

            String errorMessage = detectAndMarkError(response, apiKey);
            if (errorMessage != null) {
                return failure(model, requestBody, responseBody, httpStatus, durationMs, errorMessage);
            }

            Map<String, Object> json = JSON.parseObject(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
            if (choices == null || choices.isEmpty()) {
                return failure(model, requestBody, responseBody, httpStatus, durationMs,
                        "AI 接口返回不含 choices: " + JsonValues.truncate(responseBody, 500));
            }

            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = extractContent(message == null ? null : message.get("content"));
            String finishReason = JsonValues.asString(choice.get("finish_reason"));
            int toolCallCount = countToolCalls(message);

            Integer prompt = null, completion = null, total = null;
            if (json.get("usage") instanceof Map<?, ?> usage) {
                prompt = JsonValues.asInt(usage.get("prompt_tokens"));
                completion = JsonValues.asInt(usage.get("completion_tokens"));
                total = JsonValues.asInt(usage.get("total_tokens"));
            }

            return new ChatResult(message, content, model, requestBody, responseBody,
                    httpStatus, durationMs, prompt, completion, total,
                    finishReason, toolCallCount, null);

        } catch (Exception e) {
            return failure(model, requestBody, null, null, elapsed(start),
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * 发送一次流式（SSE）Chat Completions 请求，每个增量文本片段交给 {@code onDelta}。
     *
     * <p>与 {@link #complete} 不同，这里把异常抛给调用方（由 SSE 编排方决定如何收尾）。
     */
    public void stream(ChatRequest request, Consumer<String> onDelta) throws Exception {
        String model = resolveModel(request);
        String requestBody = JSON.toJSONString(buildBody(request, model, true));

        String apiKey = apiKeyProvider.pickTextKey();
        if (apiKey == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }

        HttpResponse<InputStream> response = httpClient.send(
                buildHttpRequest(requestBody, apiKey, request.getTimeoutSeconds()),
                HttpResponse.BodyHandlers.ofInputStream());

        int httpStatus = response.statusCode();
        if (httpStatus < 200 || httpStatus >= 300) {
            if (httpStatus == 429) {
                apiKeyProvider.markLimited(apiKey, response);
            }
            String body;
            try (InputStream in = response.body()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IllegalStateException("AI 接口返回异常状态码: " + httpStatus + ", body=" + JsonValues.truncate(body, 500));
        }

        // 个别兼容网关会用 HTTP 200 + application/json 直接回一个错误对象（而非 SSE 流）。
        // 这种情况下按非流式错误处理：读出 body、命中 429 标记限流、抛错交给 SSE 编排方收尾。
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.isBlank() && contentType.contains("json") && !contentType.contains("event-stream")) {
            String body;
            try (InputStream in = response.body()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Map<?, ?> error = findError(body == null ? "" : body.trim());
            if (error != null) {
                Integer code = JsonValues.asInt(error.get("code"));
                if (code != null && code == 429) {
                    apiKeyProvider.markLimited(apiKey, response);
                }
                String message = JsonValues.asString(error.get("message"));
                throw new IllegalStateException(StringUtils.isNotBlank(message)
                        ? message : "AI 接口返回错误: " + JsonValues.truncate(body, 500));
            }
            throw new IllegalStateException("AI 流式接口返回了非流式响应: " + JsonValues.truncate(body, 500));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    break;
                }
                Map<String, Object> chunk = JSON.parseObject(data, new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta != null && delta.get("content") instanceof String content) {
                    onDelta.accept(content);
                }
            }
        }
    }

    /** 本次调用用哪个模型：{@code ChatRequest.model} 非空则覆盖，否则回落配置的文本模型。 */
    private String resolveModel(ChatRequest request) {
        return StringUtils.isNotBlank(request.getModel())
                ? request.getModel()
                : aiConfigurationProperties.getTextModel();
    }

    private Map<String, Object> buildBody(ChatRequest request, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", request.getMessages());
        body.put("temperature", request.getTemperature());
        body.put("stream", stream);
        List<Map<String, Object>> tools = request.getTools();
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            if (StringUtils.isNotBlank(request.getToolChoice())) {
                body.put("tool_choice", request.getToolChoice());
            }
        }
        return body;
    }

    private HttpRequest buildHttpRequest(String requestBody, String apiKey, int timeoutSeconds) {
        return HttpRequest.newBuilder()
                .uri(URI.create(aiConfigurationProperties.getTextUri()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    /**
     * 探测上游错误并在命中 429 时标记限流。
     *
     * @return 错误信息；正常返回 {@code null}
     */
    private String detectAndMarkError(HttpResponse<String> response, String apiKey) {
        String responseBody = response.body();
        int httpStatus = response.statusCode();
        String trimmed = responseBody == null ? "" : responseBody.trim();

        Map<?, ?> error = findError(trimmed);
        if (error != null) {
            Integer code = JsonValues.asInt(error.get("code"));
            if ((code != null && code == 429) || httpStatus == 429) {
                apiKeyProvider.markLimited(apiKey, response);
            }
            String message = JsonValues.asString(error.get("message"));
            return StringUtils.isNotBlank(message) ? message : "AI 接口返回错误: " + JsonValues.truncate(responseBody, 500);
        }

        if (trimmed.startsWith("[")) {
            // 是数组但没解析出 error 结构，整体视为错误
            if (httpStatus == 429) {
                apiKeyProvider.markLimited(apiKey, response);
            }
            return "AI 接口返回错误: " + JsonValues.truncate(responseBody, 500);
        }

        if (httpStatus < 200 || httpStatus >= 300) {
            if (httpStatus == 429) {
                apiKeyProvider.markLimited(apiKey, response);
            }
            return "AI 接口返回异常状态码: " + httpStatus + ", body=" + JsonValues.truncate(responseBody, 500);
        }

        return null;
    }

    /** 从 {@code [ {"error":{...}} ]} 或 {@code {"error":{...}}} 两种形态里取出 error 对象。 */
    private static Map<?, ?> findError(String trimmedBody) {
        try {
            if (trimmedBody.startsWith("[")) {
                List<Map<String, Object>> list = JSON.parseObject(trimmedBody, new TypeReference<>() {});
                if (!list.isEmpty() && list.get(0).get("error") instanceof Map<?, ?> error) {
                    return error;
                }
            } else if (trimmedBody.startsWith("{")) {
                Map<String, Object> obj = JSON.parseObject(trimmedBody, new TypeReference<>() {});
                if (obj.get("error") instanceof Map<?, ?> error) {
                    return error;
                }
            }
        } catch (Exception ignored) {
            // 解析失败交给外层按状态码兜底
        }
        return null;
    }

    /** content 可能是字符串，也可能是 {@code [{type,text}]} 形态，统一抽成文本。 */
    private static String extractContent(Object content) {
        if (content instanceof String str) {
            return str;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object part : list) {
                if (part instanceof Map<?, ?> map && map.get("text") != null) {
                    sb.append(map.get("text"));
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static int countToolCalls(Map<String, Object> message) {
        if (message != null && message.get("tool_calls") instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private static ChatResult failure(String model, String requestBody, String responseBody,
                                      Integer httpStatus, long durationMs, String errorMessage) {
        return new ChatResult(null, null, model, requestBody, responseBody,
                httpStatus, durationMs, null, null, null,
                null, 0, errorMessage);
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
