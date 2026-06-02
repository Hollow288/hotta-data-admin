package com.hollow.build.ocr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * OCR 图片翻译标注任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OCR 图片翻译标注任务")
public class OcrTranslateImageTaskDto implements Serializable {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务状态: PENDING / PROCESSING / SUCCESS / FAILED")
    private String status;

    @Schema(description = "当前已重试次数")
    private Integer retryCount;

    @Schema(description = "目标语言")
    private String targetLanguage;

    @Schema(description = "置信度阈值")
    private Double minConfidence;

    @Schema(description = "识别并标注的文本块数量")
    private Integer itemCount;

    @Schema(description = "生成图片的临时访问 URL")
    private String resultImageUrl;

    @Schema(description = "结果图片所在 MinIO bucket")
    private String resultBucketName;

    @Schema(description = "结果图片 MinIO objectName")
    private String resultObjectName;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "任务创建时间戳（毫秒）")
    private Long createdAt;

    @Schema(description = "任务最后更新时间戳（毫秒）")
    private Long updatedAt;
}
