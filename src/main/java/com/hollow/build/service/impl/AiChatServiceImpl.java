package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.AiConfigurationProperties;
import com.hollow.build.dto.ChatForm;
import com.hollow.build.dto.ImageForm;
import com.hollow.build.service.AiChatService;
import com.hollow.build.utils.RedisUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final RedisUtil redisUtil;

    public AiChatServiceImpl(AiConfigurationProperties aiConfigurationProperties, RedisUtil redisUtil) {
        final var proxySelector = ProxySelector.of(new InetSocketAddress(aiConfigurationProperties.getProxyAddress(), 7890));
        this.httpClient = HttpClient.newBuilder()
                .proxy(proxySelector)
                .connectTimeout(Duration.ofSeconds(40))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.redisUtil = redisUtil;
        this.aiConfigurationProperties = aiConfigurationProperties;
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<ApiResponse<ChatForm>> chat(ChatForm chatForm) {
        try {
            String memoryId = chatForm.getMemoryId();
            if (memoryId == null || memoryId.isBlank()) {
                memoryId = UUID.randomUUID().toString();
            }

            List<Map<String, String>> messages;
            String redisKey = "chat:" + memoryId;
            Object historyJson = redisUtil.get(redisKey);
            if (historyJson != null && !historyJson.toString().isEmpty()) {
                messages = JSON.parseObject(historyJson.toString(), new TypeReference<List<Map<String, String>>>(){});
            } else {
                messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", aiConfigurationProperties.getTextDefaultPrompt()));
            }

            // 添加本次用户消息
            messages.add(Map.of("role", "user", "content", chatForm.getMessage()));

            Map<String, Object> requestBody = Map.of(
                    "model", aiConfigurationProperties.getTextModel(),
                    "messages", messages,
                    "temperature", 1,
                    "stream", false
            );

            String requestBodyJson = JSON.toJSONString(requestBody);

            String thisUseKey = getMaybeAPIAvailable("chat");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfigurationProperties.getTextUri()))
                    .header("Authorization", "Bearer " + thisUseKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();


            if (responseBody.startsWith("[")) {
                // 返回的是数组，可能是错误信息
                List<Map<String, Object>> errorList = JSON.parseObject(responseBody, new TypeReference<List<Map<String, Object>>>(){});
                Map<String, Object> errorInfo = (Map<String, Object>) errorList.get(0).get("error");

                int code = (int) errorInfo.getOrDefault("code", 0);
                String message = (String) errorInfo.getOrDefault("message", "未知错误");

                if(code == 429){
                    redisUtil.set("ai-limits-key:"+ thisUseKey, null, 86400);
                }

                // 这里可以直接返回失败响应
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), message, null)
                );
            } else {
                // 正常返回对象
                Map<String, Object> responseMap = JSON.parseObject(responseBody, new TypeReference<Map<String, Object>>(){});
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                Object content = message.get("content");

                String reply;
                if (content instanceof String str) {
                    reply = str;
                } else if (content instanceof List<?> list && !list.isEmpty()) {
                    Map<String, Object> firstItem = (Map<String, Object>) list.get(0);
                    reply = (String) firstItem.getOrDefault("text", "");
                } else {
                    reply = "";
                }

                // 保存到 Redis
                messages.add(Map.of("role", "assistant", "content", reply));
                redisUtil.set(redisKey, JSON.toJSONString(messages), 3600);

                return CompletableFuture.completedFuture(
                        ApiResponse.success(ChatForm.builder()
                                .memoryId(memoryId)
                                .message(reply)
                                .build())
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(
                    new ApiResponse<>(
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg(),
                            null
                    )
            );
        }
    }

    @Async("taskExecutor")
    @Override
    public CompletableFuture<ApiResponse<ImageForm>> image(ImageForm imageForm) {

        try {
            String thisUseKey = getMaybeAPIAvailable("image");
            String model = aiConfigurationProperties.getImageModel();

            String url = URI.create(aiConfigurationProperties.getImageUri()) + model + ":generateContent";

            String jsonBody = buildImageJsonBody(imageForm);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", thisUseKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            System.out.println("正在向 Gemini API 发送图像生成请求...");
            System.out.println("请求体: " + jsonBody);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());


            String responseBody = response.body();

            if (responseBody.startsWith("[")) {

                List<Map<String, Object>> errorList = JSON.parseObject(responseBody, new TypeReference<List<Map<String, Object>>>(){});
                Map<String, Object> errorInfo = (Map<String, Object>) errorList.get(0).get("error");

                int code = (int) errorInfo.getOrDefault("code", 0);
                String message = (String) errorInfo.getOrDefault("message", "未知错误");

                if(code == 429){
                    redisUtil.set("ai-limits-key:"+ thisUseKey, null, 86400);
                }


                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), message, null)
                );
            } else {

                Map<String, Object> responseMap = JSON.parseObject(responseBody, new TypeReference<Map<String, Object>>(){});
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                Map<String, Object> candidate = candidates.get(0);
                if(candidate.get("finishReason").equals("STOP")){
                    Map<String, Object> content = (Map<String, Object>)candidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) (content.get("parts"));
                    Map<String, Object> part = (Map<String, Object>) parts.get(0);
                    Map<String,String> inlineData = (Map<String, String>) part.get("inlineData");
                    return CompletableFuture.completedFuture(
                            ApiResponse.success(ImageForm.builder()
                                    .data(inlineData.get("data"))
                                    .mimeType(inlineData.get("mimeType"))
                                    .build())
                    );
                }else{
                    return CompletableFuture.completedFuture(
                            new ApiResponse<>(
                                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                    candidate.get("finishReason").toString(),
                                    null
                            )
                    );
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(
                    new ApiResponse<>(
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg(),
                            null
                    )
            );
        }
    }

    @Override
    public CompletableFuture<ApiResponse<ChatForm>> remove(ChatForm chatForm) {
        String memoryId = null;
        try {
            memoryId = chatForm.getMemoryId();
            if (memoryId == null || memoryId.isBlank()) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(
                                GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                "未找到对应的临时聊天记录",
                                null
                        )
                );
            }
            redisUtil.removeKey("chat:" + memoryId);

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(memoryId)
                            .message("清理成功")
                            .build())
            );

        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(
                    new ApiResponse<>(
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg(),
                            null
                    )
            );
        }
    }


    private String getMaybeAPIAvailable(String apiKeyType){
        List<String> apiKeys = List.of();
        if(apiKeyType.equals("chat")){
            apiKeys = aiConfigurationProperties.getTextApiKey();
        }

        if(apiKeyType.equals("image")){
            apiKeys = aiConfigurationProperties.getImageApiKey();
        }

        for (String apiKey : apiKeys) {
            if(!redisUtil.hasKey("ai-limits-key:" + apiKey)){
                return apiKey;
            }
        }
        return null;
    }


    private String buildImageJsonBody(ImageForm imageForm) {
        // part 内容
        Map<String, Object> userPart = new HashMap<>();
        Map<String, Object> userContent = new HashMap<>();
        userPart.put("text", escapeJson(imageForm.getMessage()));

        if(StringUtils.isNotBlank(imageForm.getData())){
            Map<String, String> userImageData = new HashMap<>();

            userImageData.put("mime_type", imageForm.getMimeType());
            userImageData.put("data", imageForm.getData());

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("inline_data", userImageData);

            userContent.put("parts", List.of(userPart, inlineData));

        }else{
            userContent.put("parts", List.of(userPart));
        }

        // contents -> parts

        userContent.put("role", "user");

        Map<String, Object> adminPart = new HashMap<>();
        adminPart.put("text",aiConfigurationProperties.getImageDefaultPrompt());
        Map<String, Object> adminContent = new HashMap<>();
        adminContent.put("parts", List.of(adminPart));
        adminContent.put("role", "model");


        // imageConfig
        Map<String, Object> imageConfig = new HashMap<>();
        imageConfig.put("aspectRatio", "9:16");

        // generationConfig
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseModalities", List.of("IMAGE"));
        generationConfig.put("imageConfig", imageConfig);

        // 根对象
        Map<String, Object> root = new HashMap<>();
        root.put("contents", List.of(adminContent,userContent));
        root.put("generationConfig", generationConfig);

        // 转 JSON 字符串
        return JSON.toJSONString(root);
    }


    /**
     * 对字符串中的特殊字符进行转义，以确保 JSON 格式正确。
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
