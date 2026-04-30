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
 *
 * <p>识别结果按 {@link #mode} 区分承载字段：
 * <ul>
 *   <li>{@code detail}：使用 {@link #results}（含坐标、置信度、页码）</li>
 *   <li>{@code list}：使用 {@link #textList}（仅文本数组）</li>
 *   <li>{@code text}：使用 {@link #fullText}（拼接后的完整字符串）</li>
 * </ul>
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
     *   <li>PROCESSING — 消费者已取出消息，正在调用远程 OCR 识别</li>
     *   <li>SUCCESS — 识别完成，结果字段（results/textList/fullText 之一）有值</li>
     *   <li>FAILED — 识别失败，errorMsg 字段包含原因</li>
     * </ul>
     */
    @Schema(description = "任务状态: PENDING / PROCESSING / SUCCESS / FAILED")
    private String status;

    /** 已进入重试队列的次数，首次提交为 0 */
    @Schema(description = "当前已重试次数")
    private Integer retryCount;

    /** 本任务使用的返回模式：{@code detail} / {@code list} / {@code text} */
    @Schema(description = "返回模式: detail / list / text")
    private String mode;

    /** detail 模式下的识别结果列表 */
    @Schema(description = "识别结果列表（mode=detail 时使用）")
    private List<OcrResultItem> results;

    /** list 模式下的纯文本数组 */
    @Schema(description = "识别文本列表（mode=list 时使用）")
    private List<String> textList;

    /** text 模式下拼接后的完整字符串 */
    @Schema(description = "拼接后的完整识别文本（mode=text 时使用）")
    private String fullText;

    /** PDF 总页数；图片识别时为 null */
    @Schema(description = "PDF 总页数；图片为 null")
    private Integer pages;

    /** 远程 OCR 服务返回的耗时（秒） */
    @Schema(description = "远程 OCR 总耗时（秒）")
    private Double elapseSeconds;

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
     * 单条 OCR 识别结果，对应远程服务 {@code mode=detail} 返回的 data 数组中的一个元素。
     * <p>
     * 远程响应格式：{@code { "text": "...", "confidence": 0.95, "bbox": [[..],[..],[..],[..]], "page": 1 }}
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

        /** 文本框 4 个顶点坐标，顺序为左上、右上、右下、左下；可能为 null */
        @Schema(description = "文本框 4 顶点坐标")
        private List<List<Double>> bbox;

        /** PDF 页码（1-based）；图片识别时为 null */
        @Schema(description = "页码（PDF 1-based；图片为 null）")
        private Integer page;
    }
}
