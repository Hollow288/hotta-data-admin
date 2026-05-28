package com.hollow.build.dto;

/**
 * OCR 翻译标注图生成结果。
 *
 * @param imageBytes 生成的 PNG 图片字节
 * @param itemCount  识别并参与标注的文本块数量
 */
public record OcrTranslatedImageResult(byte[] imageBytes, int itemCount) {
}
