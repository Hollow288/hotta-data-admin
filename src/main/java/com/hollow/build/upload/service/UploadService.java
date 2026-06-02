package com.hollow.build.upload.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.upload.dto.ChunkUploadDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传服务接口，提供文件上传、分片上传、分片合并及分片校验功能
 */
public interface UploadService {

    /**
     * 上传单个文件到指定存储桶
     *
     * @param file 待上传的文件
     * @param bucketName 存储桶名称
     * @return 包含上传结果信息的响应
     */
    ApiResponse<String> uploadFile(MultipartFile file, String bucketName);

    /**
     * 上传文件分片
     *
     * @param chunkDTO 分片上传参数，包含分片数据及元信息
     * @return 包含分片上传结果的响应
     */
    ApiResponse<String> uploadChunk(ChunkUploadDto chunkDTO);

    /**
     * 合并已上传的所有分片为完整文件
     *
     * @param chunkDTO 分片信息参数，包含文件标识等元信息
     * @return 包含合并结果的响应
     */
    ApiResponse<String> mergeChunks(ChunkUploadDto chunkDTO);

    /**
     * 检查指定文件已上传的分片列表
     *
     * @param identifier 文件唯一标识
     * @return 包含已上传分片编号列表的响应
     */
    ApiResponse<List<Integer>> checkChunks(String identifier);
}
