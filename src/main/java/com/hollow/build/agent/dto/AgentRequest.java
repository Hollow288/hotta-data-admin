package com.hollow.build.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentRequest {

    @Schema(description = "用户的自然语言请求，例如：blog_posts 表里最新的 3 篇文章是什么？")
    private String message;
}
