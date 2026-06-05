package com.hollow.build.ai.client.gemini;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ai.client.AiApiKeyProvider;
import com.hollow.build.ai.client.JsonValues;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 谷歌 Gemini 生图（{@code generateContent}）接口客户端。
 *
 * <p>统一封装「选 key → 拼 contents/parts/inline_data/generationConfig → 发请求 →
 * 判错/限流标记 → 解析 candidates/inlineData」。使用项目既有的图像模型配置
 * （{@code com.hollow.ai.image-*}），鉴权走 header {@code x-goog-api-key}。
 */
@Component
public class GeminiImageClient {

    private static final String NO_KEY_MESSAGE = "无可用 AI 图像 API Key（未配置或均已被限流）";

    /** 生图较慢，给一个较宽松的读超时（共享 HttpClient 只设了连接超时）。 */
    private static final int TIMEOUT_SECONDS = 120;

    /** 默认图片比例：竖图 9:16，沿用本次重构前的固定行为。 */
    private static final String DEFAULT_ASPECT_RATIO = "9:16";

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final AiApiKeyProvider apiKeyProvider;

    public GeminiImageClient(HttpClient aiHttpClient,
                             AiConfigurationProperties aiConfigurationProperties,
                             AiApiKeyProvider apiKeyProvider) {
        this.httpClient = aiHttpClient;
        this.aiConfigurationProperties = aiConfigurationProperties;
        this.apiKeyProvider = apiKeyProvider;
    }

    /** 默认比例（{@value #DEFAULT_ASPECT_RATIO}）生图，等价于 {@link #generateImage(String, String, String, String)} 传 null 比例。 */
    public GeminiImageResult generateImage(String prompt, String refImageBase64, String refImageMimeType) {
        return generateImage(prompt, refImageBase64, refImageMimeType, null);
    }

    /**
     * 生成图片。
     *
     * @param prompt           文本提示
     * @param refImageBase64   可选参考图（纯 base64，无 data url 前缀），为空表示纯文生图
     * @param refImageMimeType 参考图 MIME 类型，仅当 {@code refImageBase64} 非空时使用
     * @param aspectRatio      可选图片比例（如 9:16 / 1:1 / 16:9）；为空回落默认 {@value #DEFAULT_ASPECT_RATIO}
     * @return 生图结果；失败信息在 {@link GeminiImageResult#errorMessage()}，本方法不抛异常
     */
    public GeminiImageResult generateImage(String prompt, String refImageBase64,
                                           String refImageMimeType, String aspectRatio) {
        String apiKey = apiKeyProvider.pickImageKey();
        if (apiKey == null) {
            return GeminiImageResult.failure(null, NO_KEY_MESSAGE);
        }

        try {
            String model = aiConfigurationProperties.getImageModel();
            String url = URI.create(aiConfigurationProperties.getImageUri()) + model + ":generateContent";
            String jsonBody = buildJsonBody(prompt, refImageBase64, refImageMimeType, aspectRatio);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (responseBody != null && responseBody.trim().startsWith("[")) {
                List<Map<String, Object>> errorList = JSON.parseObject(responseBody, new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                Map<String, Object> errorInfo = errorList.isEmpty()
                        ? Map.of() : (Map<String, Object>) errorList.get(0).get("error");
                Integer code = JsonValues.asInt(errorInfo.get("code"));
                if ((code != null && code == 429) || response.statusCode() == 429) {
                    apiKeyProvider.markLimited(apiKey, response);
                }
                String message = JsonValues.asString(errorInfo.getOrDefault("message", "未知错误"));
                return GeminiImageResult.failure(null, message);
            }

            Map<String, Object> responseMap = JSON.parseObject(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return GeminiImageResult.failure(null, "Gemini 接口返回不含 candidates");
            }

            Map<String, Object> candidate = candidates.get(0);
            String finishReason = JsonValues.asString(candidate.get("finishReason"));
            if (!"STOP".equals(finishReason)) {
                return GeminiImageResult.failure(finishReason, String.valueOf(finishReason));
            }

            // content / parts 可能缺失；parts 里也可能先是文字、再是图，
            // 遍历找带 inlineData 的那个，避免直接 get(0) 取到文字 part 而 NPE / 越界。
            if (!(candidate.get("content") instanceof Map<?, ?> content)
                    || !(content.get("parts") instanceof List<?> parts) || parts.isEmpty()) {
                return GeminiImageResult.failure(finishReason, "Gemini 接口返回不含图片数据(parts)");
            }
            for (Object part : parts) {
                if (part instanceof Map<?, ?> p && p.get("inlineData") instanceof Map<?, ?> inlineData) {
                    Object data = inlineData.get("data");
                    if (data != null) {
                        return GeminiImageResult.success(
                                JsonValues.asString(data), JsonValues.asString(inlineData.get("mimeType")));
                    }
                }
            }
            return GeminiImageResult.failure(finishReason, "Gemini 接口返回不含图片数据(inlineData)");

        } catch (Exception e) {
            return GeminiImageResult.failure(null,
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * 构建 Gemini generateContent 请求体。
     *
     * <p>文本直接放进 Map，由 {@link JSON#toJSONString} 统一做 JSON 转义——
     * 不再手工转义，避免出现「转义两次、prompt 里的换行/引号变成字面量」的问题。
     */
    private String buildJsonBody(String prompt, String refImageBase64, String refImageMimeType, String aspectRatio) {
        Map<String, Object> userPart = new HashMap<>();
        userPart.put("text", prompt == null ? "" : prompt);

        Map<String, Object> userContent = new HashMap<>();
        if (StringUtils.isNotBlank(refImageBase64)) {
            Map<String, String> imageData = new HashMap<>();
            imageData.put("mime_type", refImageMimeType);
            imageData.put("data", refImageBase64);

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("inline_data", imageData);

            userContent.put("parts", List.of(userPart, inlineData));
        } else {
            userContent.put("parts", List.of(userPart));
        }
        userContent.put("role", "user");

        Map<String, Object> imageConfig = new HashMap<>();
        imageConfig.put("aspectRatio", StringUtils.isNotBlank(aspectRatio) ? aspectRatio : DEFAULT_ASPECT_RATIO);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseModalities", List.of("IMAGE"));
        generationConfig.put("imageConfig", imageConfig);

        Map<String, Object> root = new HashMap<>();
        root.put("contents", List.of(userContent));
        root.put("generationConfig", generationConfig);

        return JSON.toJSONString(root);
    }
}
