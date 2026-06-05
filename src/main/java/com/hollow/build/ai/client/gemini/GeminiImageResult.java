package com.hollow.build.ai.client.gemini;

/**
 * 一次 Gemini 生图调用的结果。
 *
 * <p>成功时 {@link #data}（base64）与 {@link #mimeType} 非空；
 * 失败时 {@link #errorMessage} 非空（{@link #finishReason} 可能携带非 STOP 的原因）。
 */
public record GeminiImageResult(
        String data,
        String mimeType,
        String finishReason,
        String errorMessage
) {
    public boolean isError() {
        return errorMessage != null;
    }

    static GeminiImageResult success(String data, String mimeType) {
        return new GeminiImageResult(data, mimeType, "STOP", null);
    }

    static GeminiImageResult failure(String finishReason, String errorMessage) {
        return new GeminiImageResult(null, null, finishReason, errorMessage);
    }
}
