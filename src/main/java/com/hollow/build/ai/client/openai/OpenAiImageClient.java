package com.hollow.build.ai.client.openai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ai.client.AiApiKeyProvider;
import com.hollow.build.ai.client.JsonValues;
import com.hollow.build.ai.config.AiConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI Image API 客户端。
 *
 * <p>无参考图时调用 {@code /v1/images/generations}；有参考图时调用
 * {@code /v1/images/edits}。统一封装 API Key 选取、Bearer 鉴权、429 限流标记、
 * 请求构建以及 {@code data[0].b64_json} 提取。
 */
@Component
public class OpenAiImageClient {

    private static final String NO_KEY_MESSAGE = "无可用 AI 图像 API Key（未配置或均已被限流）";
    private static final int TIMEOUT_SECONDS = 120;
    /** gpt-image-2 官方支持的横向 4K 尺寸。 */
    private static final String DEFAULT_SIZE = "3840x2160";
    private static final String DEFAULT_QUALITY = "high";
    private static final String OUTPUT_FORMAT = "png";
    private static final String OUTPUT_MIME_TYPE = "image/png";

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final AiApiKeyProvider apiKeyProvider;

    public OpenAiImageClient(HttpClient aiHttpClient,
                             AiConfigurationProperties aiConfigurationProperties,
                             AiApiKeyProvider apiKeyProvider) {
        this.httpClient = aiHttpClient;
        this.aiConfigurationProperties = aiConfigurationProperties;
        this.apiKeyProvider = apiKeyProvider;
    }

    /** 默认使用高质量横向尺寸生图。 */
    public OpenAiImageResult generateImage(String prompt, String refImageBase64, String refImageMimeType) {
        return generateImage(prompt, refImageBase64, refImageMimeType, null);
    }

    /**
     * 生成图片；传入参考图时自动切换到 Image Edits 接口。
     *
     * @param prompt           文本提示
     * @param refImageBase64   可选参考图 Base64，可带 data URL 前缀
     * @param refImageMimeType 可选参考图 MIME 类型
     * @param aspectRatio      可选比例（如 1:1、16:9、9:16）或 OpenAI 尺寸（如 1024x1536）
     * @return 生图结果；本方法不抛异常，错误信息通过 {@link OpenAiImageResult#errorMessage()} 返回
     */
    public OpenAiImageResult generateImage(String prompt, String refImageBase64,
                                           String refImageMimeType, String aspectRatio) {
        return generateImage(prompt, refImageBase64, refImageMimeType, aspectRatio, null);
    }

    /** 可选模型名称为空时使用配置的图像模型。 */
    public OpenAiImageResult generateImage(String prompt, String refImageBase64,
                                           String refImageMimeType, String aspectRatio, String model) {
        String apiKey = apiKeyProvider.pickImageKey();
        if (apiKey == null) {
            return OpenAiImageResult.failure(NO_KEY_MESSAGE);
        }

        try {
            boolean hasReferenceImage = StringUtils.isNotBlank(refImageBase64);
            String resolvedModel = StringUtils.isNotBlank(model) ? model : aiConfigurationProperties.getImageModel();
            HttpRequest request = hasReferenceImage
                    ? buildEditRequest(prompt, refImageBase64, refImageMimeType, aspectRatio, resolvedModel, apiKey)
                    : buildGenerationRequest(prompt, aspectRatio, resolvedModel, apiKey);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String errorMessage = detectAndMarkError(response, apiKey);
            if (errorMessage != null) {
                return OpenAiImageResult.failure(errorMessage);
            }

            String responseBody = response.body();
            Map<String, Object> responseMap = JSON.parseObject(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
            if (data == null || data.isEmpty()) {
                return OpenAiImageResult.failure(
                        "OpenAI 图片接口返回不含 data: " + JsonValues.truncate(responseBody, 500));
            }

            String imageBase64 = JsonValues.asString(data.get(0).get("b64_json"));
            if (StringUtils.isBlank(imageBase64)) {
                return OpenAiImageResult.failure(
                        "OpenAI 图片接口返回不含 b64_json: " + JsonValues.truncate(responseBody, 500));
            }
            return OpenAiImageResult.success(imageBase64, OUTPUT_MIME_TYPE);
        } catch (Exception e) {
            return OpenAiImageResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private HttpRequest buildGenerationRequest(String prompt, String aspectRatio, String model, String apiKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt == null ? "" : prompt);
        body.put("size", resolveSize(aspectRatio));
        body.put("quality", resolveQuality());
        body.put("output_format", OUTPUT_FORMAT);

        return baseRequest(resolveImageUri(false), apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                .build();
    }

    private HttpRequest buildEditRequest(String prompt, String refImageBase64, String refImageMimeType,
                                         String aspectRatio, String model, String apiKey) {
        ReferenceImage referenceImage = decodeReferenceImage(refImageBase64, refImageMimeType);
        String boundary = "----OpenAiImageBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody = buildMultipartBody(boundary, prompt, aspectRatio, model, referenceImage);

        return baseRequest(resolveImageUri(true), apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
    }

    private HttpRequest.Builder baseRequest(URI uri, String apiKey) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    /**
     * image-uri 推荐配置为完整 generations 地址；参考图请求会把末尾 generations 改为 edits。
     * 同时兼容配置到 /v1 或 /v1/images 的情况，方便接入 OpenAI 兼容网关。
     */
    private URI resolveImageUri(boolean edit) {
        String configured = StringUtils.stripEnd(aiConfigurationProperties.getImageUri(), "/");
        if (StringUtils.isBlank(configured)) {
            throw new IllegalStateException("未配置 AI 图片接口地址 image-uri");
        }

        String target = edit ? "edits" : "generations";
        if (configured.endsWith("/generations") || configured.endsWith("/edits")) {
            return URI.create(configured.substring(0, configured.lastIndexOf('/') + 1) + target);
        }
        if (configured.endsWith("/images")) {
            return URI.create(configured + "/" + target);
        }
        if (configured.endsWith("/v1")) {
            return URI.create(configured + "/images/" + target);
        }
        return URI.create(configured + "/" + target);
    }

    private byte[] buildMultipartBody(String boundary, String prompt, String aspectRatio, String model,
                                      ReferenceImage referenceImage) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeTextPart(output, boundary, "model", model);
            writeTextPart(output, boundary, "prompt", prompt == null ? "" : prompt);
            writeTextPart(output, boundary, "size", resolveSize(aspectRatio));
            writeTextPart(output, boundary, "quality", resolveQuality());
            writeTextPart(output, boundary, "output_format", OUTPUT_FORMAT);

            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"image[]\"; filename=\"reference."
                    + extensionFor(referenceImage.mimeType()) + "\"\r\n");
            writeAscii(output, "Content-Type: " + referenceImage.mimeType() + "\r\n\r\n");
            output.write(referenceImage.bytes());
            writeAscii(output, "\r\n--" + boundary + "--\r\n");
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("构建 OpenAI 图片编辑请求失败: " + e.getMessage(), e);
        }
    }

    private static void writeTextPart(ByteArrayOutputStream output, String boundary,
                                      String name, String value) throws Exception {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        writeAscii(output, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static ReferenceImage decodeReferenceImage(String value, String mimeType) {
        String normalized = value == null ? "" : value.trim();
        String resolvedMimeType = StringUtils.defaultIfBlank(mimeType, "image/png");
        if (normalized.startsWith("data:")) {
            int comma = normalized.indexOf(',');
            if (comma < 0 || !normalized.substring(0, comma).contains(";base64")) {
                throw new IllegalArgumentException("参考图 data URL 格式不正确");
            }
            String metadata = normalized.substring(5, comma);
            int semicolon = metadata.indexOf(';');
            if (semicolon > 0 && StringUtils.isBlank(mimeType)) {
                resolvedMimeType = metadata.substring(0, semicolon);
            }
            normalized = normalized.substring(comma + 1);
        }

        try {
            return new ReferenceImage(Base64.getMimeDecoder().decode(normalized), resolvedMimeType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("参考图不是有效的 Base64 数据", e);
        }
    }

    /** 按方向把比例映射到通用的方形、横图或竖图尺寸。 */
    private static String resolveSize(String aspectRatio) {
        if (StringUtils.isBlank(aspectRatio)) {
            return DEFAULT_SIZE;
        }
        String value = aspectRatio.trim().toLowerCase(Locale.ROOT);
        if (value.matches("\\d+x\\d+") || "auto".equals(value)) {
            return value;
        }

        String[] ratio = value.split(":");
        if (ratio.length == 2) {
            try {
                double width = Double.parseDouble(ratio[0]);
                double height = Double.parseDouble(ratio[1]);
                if (width > 0 && height > 0) {
                    if (Math.abs(width - height) < 0.0001) {
                        return "2048x2048";
                    }
                    return width > height ? "3840x2160" : "2160x3840";
                }
            } catch (NumberFormatException ignored) {
                // 非法比例回落默认横图尺寸，由上游继续按稳定格式处理。
            }
        }
        return DEFAULT_SIZE;
    }

    private String resolveQuality() {
        String configured = StringUtils.defaultIfBlank(
                aiConfigurationProperties.getImageQuality(), DEFAULT_QUALITY).toLowerCase(Locale.ROOT);
        return switch (configured) {
            case "low", "medium", "high", "auto" -> configured;
            default -> DEFAULT_QUALITY;
        };
    }

    private String detectAndMarkError(HttpResponse<String> response, String apiKey) {
        String responseBody = response.body();
        int status = response.statusCode();
        Map<?, ?> error = findError(responseBody);

        if (status == 429 || isNumericRateLimit(error)) {
            apiKeyProvider.markLimited(apiKey, response);
        }
        if (error != null) {
            String message = JsonValues.asString(error.get("message"));
            return StringUtils.isNotBlank(message)
                    ? message : "OpenAI 图片接口返回错误: " + JsonValues.truncate(responseBody, 500);
        }
        if (status < 200 || status >= 300) {
            return "OpenAI 图片接口返回异常状态码: " + status
                    + ", body=" + JsonValues.truncate(responseBody, 500);
        }
        return null;
    }

    private static boolean isNumericRateLimit(Map<?, ?> error) {
        if (error == null) {
            return false;
        }
        Integer code = JsonValues.asInt(error.get("code"));
        return code != null && code == 429;
    }

    private static Map<?, ?> findError(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(responseBody);
            if (parsed instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error) {
                return error;
            }
            if (parsed instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?> first
                    && first.get("error") instanceof Map<?, ?> error) {
                return error;
            }
        } catch (Exception ignored) {
            // 非 JSON 错误由 HTTP 状态分支返回原始响应摘要。
        }
        return null;
    }

    private static String extensionFor(String mimeType) {
        return switch (StringUtils.defaultString(mimeType).toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private record ReferenceImage(byte[] bytes, String mimeType) {
    }
}
