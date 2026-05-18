package com.hollow.build.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    @Schema(description = "本次请求 ID，可用于到 agent 日志表中追踪完整链路")
    private String requestId;

    @Schema(description = "实际处理本次请求的 agent 名，例如 database / alias")
    private String agent;

    @Schema(description = "给用户展示的稳定文本答案")
    private String answerText;

    @Schema(description = "给程序消费的结构化答案；无结构化结果时为 null")
    private Object answerData;

    @Schema(description = "调试信息：仅在 debug=true 时返回")
    private List<String> debugTrace;
}
