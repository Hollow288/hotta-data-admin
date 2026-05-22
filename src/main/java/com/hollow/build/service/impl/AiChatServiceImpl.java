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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天服务实现类，提供文本对话、图像生成、聊天记录管理及流式对话等功能。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private final HttpClient httpClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final RedisUtil redisUtil;

    /**
     * 构造方法，初始化 HttpClient、Redis 工具及 AI 配置属性。
     * 若配置了代理，则 HttpClient 会使用指定代理进行请求。
     *
     * @param aiConfigurationProperties AI 相关配置属性
     * @param redisUtil Redis 工具类
     */
    public AiChatServiceImpl(AiConfigurationProperties aiConfigurationProperties, RedisUtil redisUtil) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(40))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);

        if (aiConfigurationProperties.isProxyEnabled()) {
            ProxySelector proxySelector = ProxySelector.of(
                    new InetSocketAddress(
                            aiConfigurationProperties.getProxyAddress(),
                            aiConfigurationProperties.getProxyPort()
                    )
            );
            builder.proxy(proxySelector);
        }

        this.httpClient = builder.build();

        this.redisUtil = redisUtil;
        this.aiConfigurationProperties = aiConfigurationProperties;
    }

    /**
     * 异步发送文本聊天请求，支持多轮对话。
     * 聊天历史通过 Redis 进行缓存，超时时间为 1 小时。
     *
     * @param chatForm 聊天表单，包含消息内容和会话标识
     * @return 异步返回包含 AI 回复的聊天表单
     */
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
                String systemPrompt = aiConfigurationProperties.getTextDefaultPrompt();
                if (StringUtils.isNotBlank(systemPrompt)) {
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                }
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

    /**
     * 异步发送图像生成请求，调用 Gemini API 生成图片。
     *
     * @param imageForm 图像表单，包含提示文本及可选的参考图片数据
     * @return 异步返回包含生成图片 Base64 数据和 MIME 类型的响应
     */
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

    /**
     * 异步识别图片内容。复用 text-uri (OpenAI Chat Completions 兼容) 接口，
     * 把图片以 data URL 形式拼进 user message 的 multipart content，由具备视觉能力的对话模型返回文本描述。
     * 一次性请求，不读写 Redis 历史。
     *
     * @param imageForm 图像表单，需提供 base64 data 和 mimeType，可选 message 作为提问
     * @return 异步返回包含 AI 识别文本的响应
     */
    @Override
    @Async("taskExecutor")
    public CompletableFuture<ApiResponse<ChatForm>> recognizeImage(ImageForm imageForm) {
        try {
            if (imageForm == null
                    || StringUtils.isBlank(imageForm.getData())
                    || StringUtils.isBlank(imageForm.getMimeType())) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(
                                GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                "图片数据或类型不能为空",
                                null
                        )
                );
            }

            String question = StringUtils.isNotBlank(imageForm.getMessage())
                    ? imageForm.getMessage()
                    : "请描述这张图片的内容。";

            String dataUrl = "data:" + imageForm.getMimeType() + ";base64," + imageForm.getData();

            List<Map<String, Object>> userContent = List.of(
                    Map.of("type", "text", "text", question),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
            );

            List<Map<String, Object>> messages = new ArrayList<>();
            String systemPrompt = aiConfigurationProperties.getTextDefaultPrompt();
            if (StringUtils.isNotBlank(systemPrompt)) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userContent));

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
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (responseBody.startsWith("[")) {
                List<Map<String, Object>> errorList = JSON.parseObject(responseBody, new TypeReference<List<Map<String, Object>>>(){});
                Map<String, Object> errorInfo = (Map<String, Object>) errorList.get(0).get("error");

                int code = (int) errorInfo.getOrDefault("code", 0);
                String message = (String) errorInfo.getOrDefault("message", "未知错误");

                if (code == 429) {
                    redisUtil.set("ai-limits-key:" + thisUseKey, null, 86400);
                }

                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), message, null)
                );
            }

            Map<String, Object> responseMap = JSON.parseObject(responseBody, new TypeReference<Map<String, Object>>(){});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
            Object content = messageObj.get("content");

            String reply;
            if (content instanceof String str) {
                reply = str;
            } else if (content instanceof List<?> list && !list.isEmpty()) {
                Map<String, Object> firstItem = (Map<String, Object>) list.get(0);
                reply = (String) firstItem.getOrDefault("text", "");
            } else {
                reply = "";
            }

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(imageForm.getMemoryId())
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

    /**
     * 清除指定会话的聊天记录，从 Redis 中删除对应的历史消息。
     *
     * @param chatForm 聊天表单，包含需要清除的会话标识 memoryId
     * @return 异步返回清理结果
     */
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

    /**
     * 以 SSE（Server-Sent Events）流式方式进行聊天对话，实时推送 AI 回复内容。
     * 聊天历史同样会保存至 Redis。
     *
     * @param chatForm 聊天表单，包含消息内容和会话标识
     * @return SSE 事件发射器，用于向客户端推送流式数据
     */
    @Override
    public SseEmitter chatStream(ChatForm chatForm) {
        SseEmitter emitter = new SseEmitter(60000L);

        CompletableFuture.runAsync(() -> {
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
                    String systemPrompt = aiConfigurationProperties.getTextDefaultPrompt();
                    if (StringUtils.isNotBlank(systemPrompt)) {
                        messages.add(Map.of("role", "system", "content", systemPrompt));
                    }
                }

                messages.add(Map.of("role", "user", "content", chatForm.getMessage()));

                Map<String, Object> requestBody = Map.of(
                        "model", aiConfigurationProperties.getTextModel(),
                        "messages", messages,
                        "temperature", 1,
                        "stream", true
                );

                String thisUseKey = getMaybeAPIAvailable("chat");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(aiConfigurationProperties.getTextUri()))
                        .header("Authorization", "Bearer " + thisUseKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(requestBody)))
                        .build();

                StringBuilder fullReply = new StringBuilder();
                String finalMemoryId = memoryId;

                emitter.send(SseEmitter.event().data(Map.of("memoryId", finalMemoryId)));

                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) break;

                        Map<String, Object> chunk = JSON.parseObject(data, new TypeReference<Map<String, Object>>(){});
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                            if (delta != null && delta.containsKey("content")) {
                                String content = (String) delta.get("content");
                                fullReply.append(content);
                                emitter.send(SseEmitter.event().data(Map.of("content", content)));
                            }
                        }
                    }
                }

                messages.add(Map.of("role", "assistant", "content", fullReply.toString()));
                redisUtil.set(redisKey, JSON.toJSONString(messages), 3600);

                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }


    /**
     * 从配置的 API Key 列表中获取一个当前可用的 Key。
     * 已被限流的 Key 会在 Redis 中标记，将被跳过。
     *
     * @param apiKeyType API Key 类型，"chat" 表示文本聊天，"image" 表示图像生成
     * @return 可用的 API Key，若全部不可用则返回 null
     */
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


    /**
     * 构建 Gemini 图像生成 API 的 JSON 请求体。
     *
     * @param imageForm 图像表单，包含提示文本和可选的参考图片
     * @return JSON 格式的请求体字符串
     */
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
//        root.put("contents", List.of(adminContent,userContent));
        root.put("contents", List.of(userContent));
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
