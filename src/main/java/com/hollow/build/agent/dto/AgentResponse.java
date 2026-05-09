package com.hollow.build.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    @Schema(description = "AI 给出的最终自然语言回复")
    private String reply;

    @Schema(description = "调试信息：Agent 这一轮里依次调用过哪些工具，以及它们各自返回了什么")
    private List<String> trace;
}
