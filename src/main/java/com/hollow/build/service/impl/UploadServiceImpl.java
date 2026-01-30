package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.dto.ChunkUploadDTO;
import com.hollow.build.service.UploadService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final MinioUtil minioUtil;

    private static final String BIG_FILE_BUCKET_NAME = "big-file";

    @Override
    public ApiResponse<String> uploadFile(MultipartFile file, String bucketName) {

        if (minioUtil.bucketExists(bucketName)){

            String fileName = minioUtil.upload(file,bucketName,"");

            return ApiResponse.success(bucketName + "/" + fileName);

        }

        return new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "不存在的桶！");
    }



    @Override
    public ApiResponse<String> uploadChunk(ChunkUploadDTO chunkDTO) {
        try {
            // 构造分片在 MinIO 中的临时路径
            // 格式: temp/{identifier}/{chunkNumber}
            // 例如: temp/a8d8c.../1
            String objectName = "temp/" + chunkDTO.getIdentifier() + "/" + chunkDTO.getChunkNumber();

            minioUtil.uploadChunk(
                    BIG_FILE_BUCKET_NAME,
                    objectName,
                    chunkDTO.getFile().getInputStream(),
                    chunkDTO.getFile().getContentType()
            );

            return ApiResponse.success("分片 " + chunkDTO.getChunkNumber() + " 上传成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "上传失败: " + e.getMessage());
        }
    }

    /**
     * 2. 合并分片 (前端传完所有分片后调用此接口)
     */
    @Override
    public ApiResponse<String> mergeChunks(ChunkUploadDTO chunkDTO) {
        String identifier = chunkDTO.getIdentifier();
        String filename = chunkDTO.getFilename();
        Integer totalChunks = chunkDTO.getTotalChunks();

        // 1. 准备分片路径 (保持不变)
        List<String> chunkPaths = new ArrayList<>();
        for (int i = 1; i <= totalChunks; i++) {
            chunkPaths.add("temp/" + identifier + "/" + i);
        }

        // 2. 生成安全的存储路径 (UUID) (保持不变)
        String suffix = "";
        if (filename != null && filename.lastIndexOf(".") != -1) {
            suffix = filename.substring(filename.lastIndexOf("."));
        }
        String finalPath = "movies/" + java.util.UUID.randomUUID().toString() + suffix;

        // --- 新增：推断 Content-Type ---
        // 浏览器播放流媒体非常依赖这个
        String contentType = "video/mp4"; // 默认
        if (filename != null) {
            // 方式 A: 简单粗暴根据后缀判断
            if(filename.endsWith(".mp4")) contentType = "video/mp4";
            else if(filename.endsWith(".webm")) contentType = "video/webm";
            else if(filename.endsWith(".avi")) contentType = "video/x-msvideo";

            // 方式 B: 使用 JDK 自动推断 (推荐尝试，识别不准就用上面的)
            // String guessType = URLConnection.guessContentTypeFromName(filename);
            // if (guessType != null) contentType = guessType;
        }
        // ------------------------------

        try {
            // 3. 合并 (传入 contentType)
            // 注意这里多传了一个参数
            boolean success = minioUtil.composeFile(BIG_FILE_BUCKET_NAME, finalPath, chunkPaths, contentType);

            if (success) {
                // 4. (重要) 合并成功后，清理掉 MinIO 里的 temp 目录下的分片
                minioUtil.removeObjects(BIG_FILE_BUCKET_NAME, chunkPaths);

                // 5. 返回最终文件的访问路径或者预签名 URL
                // 如果是公开桶，直接拼 URL；如果是私有桶，生成预签名 URL
                // 这里演示生成预签名 URL (有效期 1 天)
                String playUrl = minioUtil.getPreviewFileUrl(BIG_FILE_BUCKET_NAME, finalPath, 24 * 60 * 60);

//                return playUrl;
                return ApiResponse.success(playUrl);
            } else {
                return new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "合并失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "合并异常: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse<List<Integer>> checkChunks(String identifier) {
        return ApiResponse.success(minioUtil.getChunkIndices(BIG_FILE_BUCKET_NAME, identifier));
    }
}
