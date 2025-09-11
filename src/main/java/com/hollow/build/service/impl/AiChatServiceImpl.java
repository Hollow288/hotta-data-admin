package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.AiConfigurationProperties;
import com.hollow.build.dto.ChatForm;
import com.hollow.build.service.AiChatService;
import com.hollow.build.utils.RedisUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final RedisUtil redisUtil;

    public AiChatServiceImpl(AiConfigurationProperties aiConfigurationProperties, RedisUtil redisUtil) {
        final var proxySelector = ProxySelector.of(new InetSocketAddress("127.0.0.1", 7890));
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
                messages.add(Map.of("role", "system", "content", aiConfigurationProperties.getDefaultPrompt()));
            }

            // 添加本次用户消息
            messages.add(Map.of("role", "user", "content", chatForm.getMessage()));

            Map<String, Object> requestBody = Map.of(
                    "model", aiConfigurationProperties.getModel(),
                    "messages", messages,
                    "temperature", 1,
                    "stream", false
            );

            String requestBodyJson = JSON.toJSONString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfigurationProperties.getUri()))
                    .header("Authorization", "Bearer " + aiConfigurationProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> responseMap = JSON.parseObject(response.body(), new TypeReference<Map<String, Object>>(){});
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

            // 将 AI 回复追加到 messages
            messages.add(Map.of("role", "assistant", "content", reply));

            // 存入 Redis，过期时间 3600 秒
            redisUtil.set(redisKey, JSON.toJSONString(messages), 3600);

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(memoryId)
                            .message(reply)
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
}
