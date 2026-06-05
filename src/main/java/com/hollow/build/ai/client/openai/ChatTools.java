package com.hollow.build.ai.client.openai;

import java.util.Map;

/**
 * 构造 OpenAI function-calling 的 tools 数组元素的小工具。
 *
 * <pre>{@code
 * List<Map<String,Object>> tools = List.of(
 *     ChatTools.function("get_weather", "查询天气",
 *         Map.of("type","object",
 *                "properties", Map.of("city", Map.of("type","string")),
 *                "required", List.of("city"))));
 * }</pre>
 */
public final class ChatTools {

    private ChatTools() {
    }

    /** 一个 function 工具：{@code {type:"function", function:{name, description, parameters}}}。 */
    public static Map<String, Object> function(String name, String description, Map<String, Object> parametersSchema) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parametersSchema));
    }
}
