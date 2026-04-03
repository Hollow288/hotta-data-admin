package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.ChatForm;
import com.hollow.build.dto.ImageForm;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI聊天服务接口，提供AI对话、流式对话、图像生成及对话删除等功能
 */
public interface AiChatService {

    /**
     * 发送AI聊天请求，异步返回对话结果
     *
     * @param chatForm 聊天表单，包含对话内容等信息
     * @return 包含对话结果的异步响应
     */
    CompletableFuture<ApiResponse<ChatForm>> chat(ChatForm chatForm);

    /**
     * 以SSE流式方式进行AI聊天，实时推送对话内容
     *
     * @param chatForm 聊天表单，包含对话内容等信息
     * @return SSE事件发射器，用于流式传输对话结果
     */
    SseEmitter chatStream(ChatForm chatForm);

    /**
     * 根据描述生成AI图像，异步返回图像结果
     *
     * @param imageForm 图像表单，包含图像生成的描述等信息
     * @return 包含图像生成结果的异步响应
     */
    CompletableFuture<ApiResponse<ImageForm>> image(ImageForm imageForm);

    /**
     * 删除指定的AI对话记录
     *
     * @param chatForm 聊天表单，包含待删除对话的标识信息
     * @return 包含删除操作结果的异步响应
     */
    CompletableFuture<ApiResponse<ChatForm>> remove(ChatForm chatForm);
}
