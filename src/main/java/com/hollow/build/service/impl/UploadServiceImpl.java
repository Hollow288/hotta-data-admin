package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.service.UploadService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final MinioUtil minioUtil;

    @Override
    public ApiResponse<String> uploadFile(MultipartFile file, String bucketName) {

        if (minioUtil.bucketExists(bucketName)){

            String fileName = minioUtil.upload(file,bucketName,"");

            return ApiResponse.success(bucketName + "/" + fileName);

        }

        return new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "不存在的桶！");
    }
}
