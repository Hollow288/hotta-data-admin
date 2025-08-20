package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {
    ApiResponse<String> uploadFile(MultipartFile file, String bucketName);
}
