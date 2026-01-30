package com.hollow.build.controller.v1;



import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.ChunkUploadDTO;
import com.hollow.build.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
@Tag(name = "上传", description = "上传文件")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(value = "/{bucket_name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传", description = "上传单个文件")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadFile(@RequestPart("file") MultipartFile file, @PathVariable("bucket_name") String bucketName) {
        return uploadService.uploadFile(file, bucketName);
    }


    @PostMapping("/chunk")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<String> uploadChunk(ChunkUploadDTO chunkDTO) {
        return uploadService.uploadChunk(chunkDTO);
    }

    @PostMapping("/chunk/merge")
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<String> mergeChunks(@RequestBody ChunkUploadDTO chunkDTO) {
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
