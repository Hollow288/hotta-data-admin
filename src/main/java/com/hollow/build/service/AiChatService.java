package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.ChatForm;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiChatService {
    CompletableFuture<ApiResponse<ChatForm>> chat(ChatForm chatForm);
}
