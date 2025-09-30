package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BlogDateListDto implements Serializable {

    /** 主键ID */
    @Schema(description ="主键ID")
    private Long blogId;

    /** 文章标题 */
    @Schema(description ="文章标题")
    private String title;

    /** 文章摘要 */
    @Schema(description ="文章摘要")
    private String summary;

    /** 创建时间 */
    @Schema(description ="创建时间")
    private LocalDateTime createdAt;
}
