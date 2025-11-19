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

import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "AI 聊天接口")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    @PublicEndpoint
    @Operation(summary = "聊天", description = "根据会话ID聊天")
    public CompletableFuture<ApiResponse<ChatForm>> chat(@RequestBody ChatForm chatForm) {
       return aiChatService.chat(chatForm);
    }


    @PostMapping("/image")
    @PublicEndpoint
    @Operation(summary = "图片", description = "根据会话生成图片")
    public CompletableFuture<ApiResponse<ImageForm>> image(@RequestBody ImageForm imageForm) {
        return aiChatService.image(imageForm);
    }



    @PostMapping("/remove")
    @PublicEndpoint
    @Operation(summary = "清理该ID下的临时会话记录", description = "清理该ID下的临时会话记录")
    public CompletableFuture<ApiResponse<ChatForm>> remove(@RequestBody ChatForm chatForm) {
        return aiChatService.remove(chatForm);
    }





}
