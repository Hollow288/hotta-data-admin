package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.ChunkUploadDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UploadService {
    ApiResponse<String> uploadFile(MultipartFile file, String bucketName);

    ApiResponse<String> uploadChunk(ChunkUploadDTO chunkDTO);

    ApiResponse<String> mergeChunks(ChunkUploadDTO chunkDTO);

    ApiResponse<List<Integer>> checkChunks(String identifier);
}
