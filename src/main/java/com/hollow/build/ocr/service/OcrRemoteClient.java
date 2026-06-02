package com.hollow.build.ocr.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.ocr.config.OcrConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 远程 OCR HTTP 客户端。
 * <p>
 * 统一负责调用外部 {@code POST /ocr} multipart 接口，调用方只需要传入文件字节和识别参数。
 */
@Service
@RequiredArgsConstructor
public class OcrRemoteClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final OcrConfigurationProperties ocrConfig;

    /**
     * 调用远程 OCR 服务。
     *
     * @param fileBytes     文件字节
     * @param fileName      原始文件名
     * @param contentType   MIME 类型
     * @param mode          返回模式：detail / list / text
     * @param minConfidence 可选置信度阈值
     * @return 远程服务返回的 JSON 对象
     */
    public Map<String, Object> recognize(byte[] fileBytes,
                                         String fileName,
                                         String contentType,
                                         String mode,
                                         Double minConfidence) throws Exception {
        if (ocrConfig.getServiceUrl() == null || ocrConfig.getServiceUrl().isBlank()) {
            throw new IllegalStateException("OCR 服务地址未配置");
        }

        String boundary = "----OcrBoundary" + System.currentTimeMillis();
        byte[] multipartBody = buildMultipartBody(boundary, fileName, contentType, fileBytes);
        URI endpoint = buildEndpoint(mode, minConfidence);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody));

        String apiKey = ocrConfig.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("X-API-KEY", apiKey);
        }

        HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("远程 OCR 返回异常状态码: " + response.statusCode()
                    + ", body=" + truncate(response.body(), 500));
        }

        Map<String, Object> responseMap = JSON.parseObject(response.body(), new TypeReference<>() {});
        Integer code = intOrNull(responseMap.get("code"));
        if (code != null && code != 200) {
            throw new IllegalStateException("远程 OCR 返回业务异常: code=" + code
                    + ", body=" + truncate(response.body(), 500));
        }
        return responseMap;
    }

    private URI buildEndpoint(String mode, Double minConfidence) {
        String resolvedMode = (mode == null || mode.isBlank()) ? "detail" : mode.trim();
        StringBuilder sb = new StringBuilder(ocrConfig.getServiceUrl());
        sb.append(ocrConfig.getServiceUrl().contains("?") ? '&' : '?');
        sb.append("mode=").append(URLEncoder.encode(resolvedMode, StandardCharsets.UTF_8));
        if (minConfidence != null) {
            sb.append("&min_confidence=").append(minConfidence);
        }
        return URI.create(sb.toString());
    }

    private byte[] buildMultipartBody(String boundary, String fileName, String contentType, byte[] fileBytes) {
        String lineEnd = "\r\n";
        String prefix = "--";

        String partContentType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;

        StringBuilder header = new StringBuilder();
        header.append(prefix).append(boundary).append(lineEnd);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName != null ? fileName : "upload.bin")
                .append("\"").append(lineEnd);
        header.append("Content-Type: ").append(partContentType).append(lineEnd);
        header.append(lineEnd);

        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = (lineEnd + prefix + boundary + prefix + lineEnd).getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        return body;
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

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
