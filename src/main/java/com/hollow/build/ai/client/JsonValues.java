package com.hollow.build.ai.client;

/**
 * AI client 层共用的 JSON 取值 / 截断小工具。
 *
 * <p>把原先在 {@code OpenAiChatClient}、{@code GeminiImageClient}、{@code AgentRouter} 等处
 * 各抄一份的 {@code asInt / asString / truncate} 收敛到一处，避免重复与口径不一致。
 */
public final class JsonValues {

    private JsonValues() {
    }

    /** {@code Number} 转 {@code Integer}，其余（含 null）返回 {@code null}。 */
    public static Integer asInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    /** 非 null 时 {@code toString}，null 原样返回。 */
    public static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /** 超过 {@code max} 则截断并补省略号；null 原样返回。常用于把过长报文塞进错误信息。 */
    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
