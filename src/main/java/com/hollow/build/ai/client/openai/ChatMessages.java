package com.hollow.build.ai.client.openai;

import java.util.List;
import java.util.Map;

/**
 * 构造 OpenAI messages 数组元素的小工具。
 *
 * <p>省掉到处写 {@code Map.of("role","user","content",...)} 的样板，也绕开
 * 「messages 必须声明成 {@code List<Map<String,Object>>} 否则 Map.of 泛型推断不过」的坑——
 * 这些静态方法直接返回 {@code Map<String,Object>}。
 *
 * <pre>{@code
 * List<Map<String,Object>> messages = new ArrayList<>();
 * messages.add(ChatMessages.system("你是助手"));
 * messages.add(ChatMessages.user("你好"));
 * }</pre>
 */
public final class ChatMessages {

    private ChatMessages() {
    }

    public static Map<String, Object> system(String content) {
        return text("system", content);
    }

    public static Map<String, Object> user(String content) {
        return text("user", content);
    }

    public static Map<String, Object> assistant(String content) {
        return text("assistant", content);
    }

    private static Map<String, Object> text(String role, String content) {
        return Map.of("role", role, "content", content == null ? "" : content);
    }

    /**
     * 视觉消息：一条 user 消息，含一段文本 + 一张图。
     *
     * @param text     提问文本
     * @param mimeType 图片 MIME（如 image/png）
     * @param base64   图片纯 base64（不带 data: 前缀，本方法会自动拼 data URL）
     */
    public static Map<String, Object> userWithImage(String text, String mimeType, String base64) {
        String dataUrl = "data:" + mimeType + ";base64," + base64;
        List<Map<String, Object>> parts = List.of(
                Map.of("type", "text", "text", text == null ? "" : text),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        return Map.of("role", "user", "content", parts);
    }
}
