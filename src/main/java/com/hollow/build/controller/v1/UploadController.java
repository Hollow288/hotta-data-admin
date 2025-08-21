package com.hollow.build.controller.v1;



import com.hollow.build.common.ApiResponse;
import com.hollow.build.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

}
