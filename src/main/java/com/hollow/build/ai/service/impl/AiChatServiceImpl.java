package com.hollow.build.ai.service.impl;

import com.hollow.build.ai.client.gemini.GeminiImageClient;
import com.hollow.build.ai.client.gemini.GeminiImageResult;
import com.hollow.build.ai.client.openai.ChatMessages;
import com.hollow.build.ai.client.openai.ChatRequest;
import com.hollow.build.ai.client.openai.ChatResult;
import com.hollow.build.ai.client.openai.OpenAiChatClient;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.ai.config.AiConfigurationProperties;
import com.hollow.build.ai.dto.ChatForm;
import com.hollow.build.ai.dto.ImageForm;
import com.hollow.build.ai.service.AiChatService;
import com.hollow.build.utils.RedisUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天服务实现类，提供文本对话、图像生成、聊天记录管理及流式对话等功能。
 *
 * <p>本类只负责业务编排：会话历史（Redis）、memoryId、system prompt 拼装、{@code ApiResponse} 封装、
 * 异步与 SSE 事件编排。具体「怎么跟 AI 说话」（HTTP / 协议 / key / 限流）交给
 * {@link OpenAiChatClient} 与 {@link GeminiImageClient}。
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final OpenAiChatClient openAiChatClient;
    private final GeminiImageClient geminiImageClient;
    private final AiConfigurationProperties aiConfigurationProperties;
    private final RedisUtil redisUtil;

    public AiChatServiceImpl(OpenAiChatClient openAiChatClient,
                             GeminiImageClient geminiImageClient,
                             AiConfigurationProperties aiConfigurationProperties,
                             RedisUtil redisUtil) {
        this.openAiChatClient = openAiChatClient;
        this.geminiImageClient = geminiImageClient;
        this.aiConfigurationProperties = aiConfigurationProperties;
        this.redisUtil = redisUtil;
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
            String memoryId = resolveMemoryId(chatForm.getMemoryId());
            String redisKey = "chat:" + memoryId;

            List<Map<String, Object>> messages = loadHistory(redisKey);
            messages.add(ChatMessages.user(chatForm.getMessage()));

            ChatResult result = openAiChatClient.complete(
                    ChatRequest.builder().messages(messages).temperature(1).build());

            if (result.isError()) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                result.errorMessage(), null));
            }

            String reply = result.content();
            messages.add(ChatMessages.assistant(reply));
            redisUtil.set(redisKey, JSON.toJSONString(messages), 3600);

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(memoryId)
                            .message(reply)
                            .build()));
        } catch (Exception e) {
            log.error("AI 调用处理异常", e);
            return internalError();
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
            GeminiImageResult result = geminiImageClient.generateImage(
                    imageForm.getMessage(), imageForm.getData(), imageForm.getMimeType(),
                    imageForm.getAspectRatio());

            if (result.isError()) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                result.errorMessage(), null));
            }

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ImageForm.builder()
                            .data(result.data())
                            .mimeType(result.mimeType())
                            .build()));
        } catch (Exception e) {
            log.error("AI 调用处理异常", e);
            return internalError();
        }
    }

    /**
     * 异步识别图片内容。复用 text 模型（OpenAI Chat Completions 兼容）接口，
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
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                "图片数据或类型不能为空", null));
            }

            String question = StringUtils.isNotBlank(imageForm.getMessage())
                    ? imageForm.getMessage()
                    : "请描述这张图片的内容。";

            List<Map<String, Object>> messages = new ArrayList<>();
            String systemPrompt = aiConfigurationProperties.getTextDefaultPrompt();
            if (StringUtils.isNotBlank(systemPrompt)) {
                messages.add(ChatMessages.system(systemPrompt));
            }
            messages.add(ChatMessages.userWithImage(question, imageForm.getMimeType(), imageForm.getData()));

            ChatResult result = openAiChatClient.complete(
                    ChatRequest.builder().messages(messages).temperature(1).timeoutSeconds(120).build());

            if (result.isError()) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                result.errorMessage(), null));
            }

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(imageForm.getMemoryId())
                            .message(result.content())
                            .build()));
        } catch (Exception e) {
            log.error("AI 调用处理异常", e);
            return internalError();
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
        try {
            String memoryId = chatForm.getMemoryId();
            if (memoryId == null || memoryId.isBlank()) {
                return CompletableFuture.completedFuture(
                        new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                                "未找到对应的临时聊天记录", null));
            }
            redisUtil.removeKey("chat:" + memoryId);

            return CompletableFuture.completedFuture(
                    ApiResponse.success(ChatForm.builder()
                            .memoryId(memoryId)
                            .message("清理成功")
                            .build()));
        } catch (Exception e) {
            log.error("AI 调用处理异常", e);
            return internalError();
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
                String memoryId = resolveMemoryId(chatForm.getMemoryId());
                String redisKey = "chat:" + memoryId;

                List<Map<String, Object>> messages = loadHistory(redisKey);
                messages.add(ChatMessages.user(chatForm.getMessage()));

                StringBuilder fullReply = new StringBuilder();
                emitter.send(SseEmitter.event().data(Map.of("memoryId", memoryId)));

                openAiChatClient.stream(
                        ChatRequest.builder().messages(messages).temperature(1).build(),
                        delta -> {
                            fullReply.append(delta);
                            try {
                                emitter.send(SseEmitter.event().data(Map.of("content", delta)));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                messages.add(ChatMessages.assistant(fullReply.toString()));
                redisUtil.set(redisKey, JSON.toJSONString(messages), 3600);

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 生成或沿用 memoryId。 */
    private String resolveMemoryId(String memoryId) {
        return (memoryId == null || memoryId.isBlank()) ? UUID.randomUUID().toString() : memoryId;
    }

    /** 从 Redis 读取会话历史；无历史时初始化并按需带上 system prompt。 */
    private List<Map<String, Object>> loadHistory(String redisKey) {
        Object historyJson = redisUtil.get(redisKey);
        if (historyJson != null && !historyJson.toString().isEmpty()) {
            return JSON.parseObject(historyJson.toString(), new TypeReference<List<Map<String, Object>>>() {});
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = aiConfigurationProperties.getTextDefaultPrompt();
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(ChatMessages.system(systemPrompt));
        }
        return messages;
    }

    private <T> CompletableFuture<ApiResponse<T>> internalError() {
        return CompletableFuture.completedFuture(
                new ApiResponse<>(
                        GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                        GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg(),
                        null));
    }
}
