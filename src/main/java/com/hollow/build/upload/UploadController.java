package com.hollow.build.upload;



import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.upload.dto.ChunkUploadDto;
import com.hollow.build.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/**
 * 文件上传控制器，提供普通上传、分片上传、分片合并和断点续传检查接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
@Tag(name = "上传", description = "上传文件")
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传单个文件到指定存储桶。
     *
     * @param file 待上传的文件
     * @param bucketName 目标存储桶名称
     * @return 包含上传后文件路径的响应结果
     */
    @PostMapping(value = "/{bucket_name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传", description = "上传单个文件")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadFile(@RequestPart("file") MultipartFile file, @PathVariable("bucket_name") String bucketName) {
        return uploadService.uploadFile(file, bucketName);
    }


    /**
     * 上传单个文件分片。
     *
     * @param chunkDTO 分片上传参数，包含文件标识、分片序号和文件内容
     * @return 包含分片上传结果的响应结果
     */
    @PostMapping("/chunk")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<String> uploadChunk(ChunkUploadDto chunkDTO) {
        return uploadService.uploadChunk(chunkDTO);
    }

    /**
     * 合并已上传完成的全部分片。
     *
     * @param chunkDTO 分片合并参数，包含文件标识、文件名和分片总数
     * @return 包含最终访问地址或合并结果的响应结果
     */
    @PostMapping("/chunk/merge")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<String> mergeChunks(@RequestBody ChunkUploadDto chunkDTO) {
        return uploadService.mergeChunks(chunkDTO);
    }

    /**
     * 检查断点续传状态
     * @param identifier 文件的MD5
     * @return 已上传的分片序号列表
     */
    @GetMapping("/chunk/check")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<Integer>> checkChunks(@RequestParam("identifier") String identifier) {
        // 去 MinIO 查一下 temp/{identifier} 下面有哪些文件
        return uploadService.checkChunks(identifier);
    }

}
