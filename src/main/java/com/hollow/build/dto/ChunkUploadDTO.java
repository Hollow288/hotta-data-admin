package com.hollow.build.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ChunkUploadDTO {
    // 文件的唯一标识 (MD5)
    private String identifier;

    // 当前分片序号 (1, 2, 3...)
    private Integer chunkNumber;

    // 分片文件实体
    private MultipartFile file;

    // 文件总大小 (可选)
    private Long totalSize;

    // 原始文件名 (仅在合并时或第一个分片时需要)
    private String filename;

    // 总分片数 (仅在合并时需要)
    private Integer totalChunks;
}