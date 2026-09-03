package com.hollow.build.ai.client.openai;

/**
 * 一次 OpenAI 图片生成或编辑调用的结果。
 *
 * <p>成功时 {@link #data}（Base64）与 {@link #mimeType} 非空；
 * 失败时 {@link #errorMessage} 非空。
 */
public record OpenAiImageResult(
        String data,
        String mimeType,
        String errorMessage
) {
    public boolean isError() {
        return errorMessage != null;
    }

    static OpenAiImageResult success(String data, String mimeType) {
        return new OpenAiImageResult(data, mimeType, null);
    }

    static OpenAiImageResult failure(String errorMessage) {
        return new OpenAiImageResult(null, null, errorMessage);
    }
}
