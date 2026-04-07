package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * OCR 任务数据传输对象，贯穿整个 OCR 异步处理流程。
 * <p>
 * 该对象同时用于：
 * <ul>
 *   <li><b>提交任务的响应</b>：返回 taskId 和初始状态 PENDING</li>
 *   <li><b>Redis 中的任务存储</b>：序列化为 JSON 存入 Redis，key 为 "ocr:result:{taskId}"</li>
 *   <li><b>轮询结果的响应</b>：从 Redis 反序列化后返回给前端</li>
 * </ul>
 *
 * <p>状态流转：PENDING → PROCESSING → SUCCESS / FAILED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OCR 任务")
public class OcrTaskDto implements Serializable {

    /** 任务唯一标识，由 UUID 生成，前端凭此轮询结果 */
    @Schema(description = "任务ID")
    private String taskId;

    /**
     * 任务当前状态：
     * <ul>
     *   <li>PENDING — 已提交到 MQ 队列，排队等待处理</li>
     *   <li>PROCESSING — 消费者已取出消息，正在调用 RapidOCR 识别</li>
     *   <li>SUCCESS — 识别完成，results 字段包含结果</li>
     *   <li>FAILED — 识别失败，errorMsg 字段包含原因</li>
     * </ul>
     */
    @Schema(description = "任务状态: PENDING / PROCESSING / SUCCESS / FAILED")
    private String status;

    /** 已进入重试队列的次数，首次提交为 0 */
    @Schema(description = "当前已重试次数")
    private Integer retryCount;

    /** 识别结果列表，仅在 status 为 SUCCESS 时有值 */
    @Schema(description = "识别结果列表")
    private List<OcrResultItem> results;

    /** 错误信息，仅在 status 为 FAILED 时有值 */
    @Schema(description = "错误信息")
    private String errorMsg;

    /** 任务创建时间戳（毫秒） */
    @Schema(description = "任务创建时间戳（毫秒）")
    private Long createdAt;

    /** 任务最后更新时间戳（毫秒） */
    @Schema(description = "任务最后更新时间戳（毫秒）")
    private Long updatedAt;

    /**
     * 单条 OCR 识别结果，对应 RapidOCR 返回的 data 数组中的一个元素。
     * <p>
     * RapidOCR 原始响应格式：{@code { "text": "识别到的文字", "confidence": 0.95 }}
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrResultItem implements Serializable {

        /** 识别到的文本内容 */
        @Schema(description = "识别文本")
        private String text;

        /** 识别置信度，范围 0~1，值越大表示识别结果越可信 */
        @Schema(description = "置信度")
        private Double confidence;
    }
}
