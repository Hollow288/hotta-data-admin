package com.hollow.build.controller.v1;



import com.hollow.build.common.ApiResponse;
import com.hollow.build.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/{bucket_name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadFile(@RequestPart("file") MultipartFile file, @PathVariable("bucket_name") String bucketName) {
        return uploadService.uploadFile(file, bucketName);
    }

}
