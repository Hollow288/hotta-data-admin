package com.hollow.build.entity.mysql;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 博客文章实体类
 */
@Data
public class BlogPost implements Serializable {

    /** 主键ID */
    @Schema(description ="主键ID")
    private Long blogId;

    /** 文章标题 */
    @Schema(description ="文章标题")
    private String title;

    /** 文章摘要 */
    @Schema(description ="文章摘要")
    private String summary;

    /** 文章正文（Markdown 字符串） */
    @Schema(description ="文章正文（Markdown 字符串）")
    private String content;

    /** 创建时间 */
    @Schema(description ="创建时间")
    private LocalDateTime createdAt;

    /**
     * 是否删除（0未删除 1已删除）
     */
    @Schema(description ="是否删除（0未删除 1已删除）")
    private String delFlag;
}

