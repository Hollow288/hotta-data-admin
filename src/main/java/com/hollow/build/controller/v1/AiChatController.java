package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.ChatForm;
import com.hollow.build.dto.ImageForm;
import com.hollow.build.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天控制器，提供 AI 对话、流式对话、图片生成及会话清理等功能
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "AI 聊天接口")
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * AI 聊天接口，根据会话ID进行对话
     *
     * @param chatForm 聊天表单，包含会话ID及消息内容
     * @return 异步返回聊天结果
     */
    @PostMapping("/chat")
    @PublicEndpoint
    @Operation(summary = "聊天", description = "根据会话ID聊天")
    public CompletableFuture<ApiResponse<ChatForm>> chat(@RequestBody ChatForm chatForm) {
       return aiChatService.chat(chatForm);
    }

    /**
     * AI 流式聊天接口，通过 SSE 实时推送对话内容
     *
     * @param chatForm 聊天表单，包含会话ID及消息内容
     * @return SSE 事件发射器，用于流式返回聊天内容
     */
    @PostMapping("/chat/stream")
    @PublicEndpoint
    @Operation(summary = "流式聊天", description = "根据会话ID流式聊天")
    public SseEmitter chatStream(@RequestBody ChatForm chatForm) {
        return aiChatService.chatStream(chatForm);
    }

    /**
     * AI 图片生成接口，根据会话内容生成图片
     *
     * @param imageForm 图片表单，包含图片生成的相关参数
     * @return 异步返回生成的图片信息
     */
    @PostMapping("/image")
    @PublicEndpoint
    @Operation(summary = "图片", description = "根据会话生成图片")
    public CompletableFuture<ApiResponse<ImageForm>> image(@RequestBody ImageForm imageForm) {
        return aiChatService.image(imageForm);
    }

    /**
     * AI 图片识别接口，根据上传的图片返回文本识别结果
     *
     * @param imageForm 图片表单，需提供 base64 data 与 mimeType，可选 message 作为提问
     * @return 异步返回 AI 对图片的文本描述
     */
    @PostMapping("/image/recognize")
    @PublicEndpoint
    @Operation(summary = "图片识别", description = "识别图片内容并返回文本描述")
    public CompletableFuture<ApiResponse<ChatForm>> recognizeImage(@RequestBody ImageForm imageForm) {
        return aiChatService.recognizeImage(imageForm);
    }



    /**
     * 清理指定会话ID下的临时会话记录
     *
     * @param chatForm 聊天表单，包含需要清理的会话ID
     * @return 异步返回清理结果
     */
    @PostMapping("/remove")
    @PublicEndpoint
    @Operation(summary = "清理该ID下的临时会话记录", description = "清理该ID下的临时会话记录")
    public CompletableFuture<ApiResponse<ChatForm>> remove(@RequestBody ChatForm chatForm) {
        return aiChatService.remove(chatForm);
    }





}
