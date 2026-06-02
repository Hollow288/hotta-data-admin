package com.hollow.build.ocr.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ai.config.AiConfigurationProperties;
import com.hollow.build.ocr.config.OcrConfigurationProperties;
import com.hollow.build.ocr.dto.OcrTranslatedImageResult;
import com.hollow.build.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.*;

/**
 * OCR 图片翻译标注任务处理器。
 * <p>
 * 该类不负责入队和状态存储，只执行 OCR、翻译并委托 {@link OcrTranslationImageRenderer} 绘图，供 MQ consumer 调用。
 */
@Slf4j
@Service
public class OcrTranslateImageProcessor {

    private final OcrRemoteClient ocrRemoteClient;
    private final OcrConfigurationProperties ocrConfig;
    private final AiConfigurationProperties aiConfig;
    private final RedisUtil redisUtil;
    private final OcrTranslationImageRenderer imageRenderer;
    private final HttpClient httpClient;

    public OcrTranslateImageProcessor(OcrRemoteClient ocrRemoteClient,
                                      OcrConfigurationProperties ocrConfig,
                                      AiConfigurationProperties aiConfig,
                                      RedisUtil redisUtil,
                                      OcrTranslationImageRenderer imageRenderer) {
        this.ocrRemoteClient = ocrRemoteClient;
        this.ocrConfig = ocrConfig;
        this.aiConfig = aiConfig;
        this.redisUtil = redisUtil;
        this.imageRenderer = imageRenderer;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(40))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);

        if (aiConfig.isProxyEnabled()) {
            builder.proxy(ProxySelector.of(
                    new InetSocketAddress(aiConfig.getProxyAddress(), aiConfig.getProxyPort())
            ));
        }

        this.httpClient = builder.build();
    }

    public OcrTranslatedImageResult process(byte[] imageBytes,
                                            String fileName,
                                            String contentType,
                                            String targetLanguage,
                                            Double minConfidence) {
        try {
            Double resolvedMinConfidence = resolveMinConfidence(minConfidence);
            String resolvedTargetLanguage = StringUtils.defaultIfBlank(targetLanguage, "中文").trim();

            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (sourceImage == null) {
                throw new IllegalArgumentException("无法读取图片内容，请上传 JPG/PNG/BMP 等可解码图片");
            }

            long ocrStart = System.currentTimeMillis();
            Map<String, Object> ocrResponse = ocrRemoteClient.recognize(
                    imageBytes,
                    fileName,
                    contentType,
                    "detail",
                    resolvedMinConfidence
            );
            long ocrMillis = System.currentTimeMillis() - ocrStart;

            List<DetectedText> detectedTexts = parseDetectedTexts(ocrResponse);

            long translateStart = System.currentTimeMillis();
            Map<Integer, String> translations = translateTexts(detectedTexts, resolvedTargetLanguage);
            long translateMillis = System.currentTimeMillis() - translateStart;

            List<OcrTranslationImageRenderer.OcrTextItem> items = detectedTexts.stream()
                    .map(item -> new OcrTranslationImageRenderer.OcrTextItem(
                            item.index(),
                            item.text(),
                            translations.getOrDefault(item.index(), item.text()),
                            item.bbox()
                    ))
                    .toList();
            byte[] outputBytes = imageRenderer.render(sourceImage, items, resolvedTargetLanguage,
                    new OcrTranslationImageRenderer.RenderStats(ocrMillis, translateMillis));

            return new OcrTranslatedImageResult(outputBytes, detectedTexts.size());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成 OCR 翻译标注图失败", e);
            throw new IllegalStateException("生成 OCR 翻译标注图失败: " + safeMessage(e), e);
        }
    }

    private Double resolveMinConfidence(Double minConfidence) {
        Double candidate = minConfidence != null ? minConfidence : ocrConfig.getDefaultMinConfidence();
        if (candidate == null) {
            return null;
        }
        if (candidate < 0.0 || candidate > 1.0) {
            throw new IllegalArgumentException("minConfidence 必须在 [0, 1] 之间");
        }
        return candidate;
    }

    private List<DetectedText> parseDetectedTexts(Map<String, Object> ocrResponse) {
        Object dataNode = ocrResponse.get("data");
        if (!(dataNode instanceof List<?> rawItems)) {
            return List.of();
        }

        List<DetectedText> detectedTexts = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                continue;
            }

            String text = stringValue(item.get("text"));
            if (StringUtils.isBlank(text)) {
                continue;
            }

            List<Point2D.Double> bbox = bboxValue(item.get("bbox"));
            if (bbox.size() < 4) {
                continue;
            }

            detectedTexts.add(new DetectedText(
                    detectedTexts.size() + 1,
                    text.trim(),
                    bbox
            ));
        }
        return detectedTexts;
    }

    private Map<Integer, String> translateTexts(List<DetectedText> detectedTexts, String targetLanguage) throws Exception {
        if (detectedTexts.isEmpty()) {
            return Map.of();
        }

        if (StringUtils.isBlank(aiConfig.getTextUri()) || StringUtils.isBlank(aiConfig.getTextModel())) {
            throw new IllegalStateException("AI 文本服务地址或模型未配置");
        }

        String apiKey = getMaybeApiAvailable();
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("AI 文本服务 API Key 未配置或均已限流");
        }

        Map<Integer, String> translations = new HashMap<>();
        int batchSize = 50;
        for (int from = 0; from < detectedTexts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, detectedTexts.size());
            translations.putAll(translateBatch(detectedTexts.subList(from, to), targetLanguage, apiKey));
        }
        detectedTexts.forEach(item -> translations.putIfAbsent(item.index(), item.text()));
        return translations;
    }

    private Map<Integer, String> translateBatch(List<DetectedText> detectedTexts,
                                                String targetLanguage,
                                                String apiKey) throws Exception {
        List<Map<String, Object>> items = detectedTexts.stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("index", item.index());
                    map.put("text", item.text());
                    return map;
                })
                .toList();

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("targetLanguage", targetLanguage);
        userPayload.put("items", items);

        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                你是 OCR 文本翻译器。把用户给出的 items[].text 翻译成 targetLanguage。
                                必须只返回 JSON，不要返回 Markdown，不要解释。
                                格式固定为 {"translations":[{"index":1,"text":"译文"}]}。
                                index 必须原样返回；专有名词、数字和符号尽量保留。
                                """
                ),
                Map.of(
                        "role", "user",
                        "content", JSON.toJSONString(userPayload)
                )
        );

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", aiConfig.getTextModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0);
        requestBody.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiConfig.getTextUri()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            redisUtil.set("ai-limits-key:" + apiKey, null, 86400);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI 翻译服务返回异常状态码: " + response.statusCode()
                    + ", body=" + truncate(response.body(), 500));
        }

        String translatedContent = extractChatContent(response.body(), apiKey);
        return parseTranslationJson(translatedContent);
    }

    private String extractChatContent(String responseBody, String apiKey) {
        String trimmedBody = responseBody == null ? "" : responseBody.trim();
        if (trimmedBody.startsWith("[")) {
            List<Map<String, Object>> errorList = JSON.parseObject(trimmedBody, new TypeReference<>() {});
            if (!errorList.isEmpty() && errorList.get(0).get("error") instanceof Map<?, ?> error) {
                markLimitedKeyIfNeeded(error, apiKey);
                throw new IllegalStateException("AI 翻译服务返回错误: " + stringValue(error.get("message")));
            }
        }

        Map<String, Object> responseMap = JSON.parseObject(trimmedBody, new TypeReference<>() {});
        if (responseMap.get("error") instanceof Map<?, ?> error) {
            markLimitedKeyIfNeeded(error, apiKey);
            throw new IllegalStateException("AI 翻译服务返回错误: " + stringValue(error.get("message")));
        }

        Object choicesNode = responseMap.get("choices");
        if (!(choicesNode instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("AI 翻译服务响应缺少 choices");
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("AI 翻译服务 choices 格式异常");
        }
        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("AI 翻译服务响应缺少 message");
        }

        Object content = message.get("content");
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> partMap && partMap.get("text") != null) {
                    sb.append(partMap.get("text"));
                }
            }
            return sb.toString();
        }
        throw new IllegalStateException("AI 翻译服务响应缺少文本内容");
    }

    private void markLimitedKeyIfNeeded(Map<?, ?> error, String apiKey) {
        Integer code = intOrNull(error.get("code"));
        if (code != null && code == 429) {
            redisUtil.set("ai-limits-key:" + apiKey, null, 86400);
        }
    }

    private Map<Integer, String> parseTranslationJson(String content) {
        String jsonPayload = extractJsonPayload(content);
        List<Map<String, Object>> rawTranslations;

        if (jsonPayload.startsWith("[")) {
            rawTranslations = JSON.parseObject(jsonPayload, new TypeReference<>() {});
        } else {
            Map<String, Object> root = JSON.parseObject(jsonPayload, new TypeReference<>() {});
            Object translationsNode = root.get("translations");
            if (!(translationsNode instanceof List<?>)) {
                throw new IllegalStateException("AI 翻译返回缺少 translations 数组");
            }
            rawTranslations = JSON.parseObject(JSON.toJSONString(translationsNode), new TypeReference<>() {});
        }

        Map<Integer, String> translations = new HashMap<>();
        for (Map<String, Object> item : rawTranslations) {
            Integer index = intOrNull(item.get("index"));
            if (index == null || index <= 0) {
                continue;
            }
            translations.put(index, StringUtils.defaultString(stringValue(item.get("text"))));
        }
        return translations;
    }

    private String extractJsonPayload(String content) {
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("AI 翻译返回为空");
        }

        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.trim();
        }

        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        char endChar;
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            endChar = '}';
        } else if (arrayStart >= 0) {
            start = arrayStart;
            endChar = ']';
        } else {
            throw new IllegalStateException("AI 翻译返回不是 JSON: " + truncate(content, 200));
        }

        int end = text.lastIndexOf(endChar);
        if (end < start) {
            throw new IllegalStateException("AI 翻译返回 JSON 不完整: " + truncate(content, 200));
        }
        return text.substring(start, end + 1);
    }

    private List<Point2D.Double> bboxValue(Object value) {
        if (!(value instanceof List<?> outer)) {
            return List.of();
        }
        List<Point2D.Double> points = new ArrayList<>();
        for (Object point : outer) {
            if (!(point instanceof List<?> coordinates) || coordinates.size() < 2) {
                continue;
            }
            Double x = doubleValue(coordinates.get(0));
            Double y = doubleValue(coordinates.get(1));
            if (x != null && y != null) {
                points.add(new Point2D.Double(x, y));
            }
        }
        return points;
    }

    private String getMaybeApiAvailable() {
        List<String> apiKeys = aiConfig.getTextApiKey();
        if (apiKeys == null || apiKeys.isEmpty()) {
            return null;
        }
        for (String apiKey : apiKeys) {
            if (StringUtils.isNotBlank(apiKey) && !redisUtil.hasKey("ai-limits-key:" + apiKey)) {
                return apiKey;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "未知错误";
        }
        return exception.getMessage();
    }

    private record DetectedText(int index, String text, List<Point2D.Double> bbox) {
    }
}
