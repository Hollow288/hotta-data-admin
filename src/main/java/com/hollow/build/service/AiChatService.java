package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.ChatForm;
import com.hollow.build.dto.ImageForm;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiChatService {
    CompletableFuture<ApiResponse<ChatForm>> chat(ChatForm chatForm);

    CompletableFuture<ApiResponse<ImageForm>> image(ImageForm imageForm);

    CompletableFuture<ApiResponse<ChatForm>> remove(ChatForm chatForm);
}
