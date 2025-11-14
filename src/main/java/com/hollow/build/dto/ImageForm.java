package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ImageForm implements Serializable {

    @Schema(description = "会话ID")
    private String memoryId;

    @Schema(description = "消息内容")
    private String message;

    @Schema(description = "原图片内容")
    private String data;

    @Schema(description = "原图片格式")
    private String mimeType;

    @Schema(description = "图片比例")
    private String aspectRatio;
}
