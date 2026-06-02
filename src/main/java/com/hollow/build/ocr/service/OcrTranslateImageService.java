package com.hollow.build.ocr.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ocr.dto.OcrTranslateImageTaskDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 图片翻译标注任务服务。
 */
public interface OcrTranslateImageService {

    /**
     * 提交 OCR 图片翻译标注任务。
     */
    ApiResponse<OcrTranslateImageTaskDto> submitTask(MultipartFile file,
                                                     String targetLanguage,
                                                     Double minConfidence);

    /**
     * 查询 OCR 图片翻译标注任务状态。
     */
    ApiResponse<OcrTranslateImageTaskDto> getResult(String taskId);
}
