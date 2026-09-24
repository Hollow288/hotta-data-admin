# OpenAiChatClient 使用指南

OpenAI API 封装。`OpenAiChatClient` 覆盖普通对话、视觉识图、function calling、流式；
`OpenAiImageClient` 负责图片生成与基于参考图的编辑。

```java
private final OpenAiChatClient openAiChatClient; // 构造注入即可
private final OpenAiImageClient openAiImageClient;
```

核心两个方法：

```java
ChatResult complete(ChatRequest request);                          // 非流式，返回完整结果
void        stream(ChatRequest request, Consumer<String> onDelta); // 流式，每个增量片段回调
OpenAiImageResult generateImage(String prompt, String refBase64, String refMimeType, String aspectRatio);
OpenAiImageResult generateImage(String prompt, String refBase64, String refMimeType, String aspectRatio, String model);
```

`OpenAiImageClient` 在没有参考图时请求 `image-uri` 指向的 `/v1/images/generations`；传入参考图时，
会自动改用 `/v1/images/edits` multipart 请求。成功结果的 `data()` 是 Base64 PNG，失败时先判断
`isError()` 并读取 `errorMessage()`。默认输出横向 4K `3840x2160`，质量由 `image-quality` 控制，
默认值为 `high`；请求显式传入 `aspectRatio` 时会覆盖默认横图方向。
`model` 可覆盖本次图片生成或编辑使用的模型；不传或传空值时使用 `image-model` 配置。

> 4K 尺寸仅按 `gpt-image-2` 能力设置，且 OpenAI 当前将超过 `2560x1440` 的输出标记为 experimental。

---

## ChatRequest：你要告诉模型什么

用 builder 构造，只有 `messages` 必填：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `messages` | `List<Map<String,Object>>` | 必填 | OpenAI messages 数组 |
| `temperature` | `double` | `1.0` | 采样温度 |
| `tools` | `List<Map<String,Object>>` | 不传 | function calling 工具数组；为空则不下发 |
| `toolChoice` | `String` | 不传 | 如 `"auto"` / `"required"`；仅在有 tools 时生效 |
| `timeoutSeconds` | `int` | `60` | 本次请求读超时 |

> **泛型小贴士**：messages 一定要声明成 `List<Map<String,Object>>`（不要用 `var` 或 `List<Map<String,String>>`）。
> 只要目标类型是 `Object`，`Map.of("role","user","content", x)` 就能正常放进去——无论 content 是字符串还是嵌套结构。
> 嫌麻烦就用下面的 `ChatMessages` 工厂，直接省掉这层心智负担。

---

## ChatResult：你能拿到什么

`complete()` 返回的 record，**先判 `isError()`**：

| 字段 | 说明 |
|---|---|
| `isError()` | 失败返回 true（即 `errorMessage != null`） |
| `errorMessage()` | 失败原因（上游错误 message / 状态码 / 网络异常 / 无可用 key） |
| `content()` | 提取好的文本回复（String 或多段 text 已拼好） |
| `message()` | assistant 原始 message（**取 `tool_calls` 用这个**） |
| `finishReason()` / `toolCallCount()` | 停止原因 / 本轮工具调用数 |
| `promptTokens()` / `completionTokens()` / `totalTokens()` | token 统计 |
| `model()` / `requestBody()` / `responseBody()` / `httpStatus()` / `durationMs()` | 元数据，给日志用 |

标准用法：

```java
ChatResult r = openAiChatClient.complete(req);
if (r.isError()) {
    // 记日志 / 返回错误，errorMessage 可直接给用户看
    return ApiResponse.fail(r.errorMessage());
}
String reply = r.content();
```

---

## 省样板：`ChatMessages` / `ChatTools`

不想到处写 `Map.of("role",...)`、也不想踩泛型坑，用这两个工厂（同包）：

```java
messages.add(ChatMessages.system("你是助手"));
messages.add(ChatMessages.user("你好"));
messages.add(ChatMessages.assistant(reply));
messages.add(ChatMessages.userWithImage("这是什么？", "image/png", base64)); // 视觉，自动拼 data URL

List<Map<String, Object>> tools = List.of(
        ChatTools.function("get_weather", "查询某城市天气",
                Map.of("type", "object",
                       "properties", Map.of("city", Map.of("type", "string")),
                       "required", List.of("city"))));
```

下面的例子都用它们。

---

## 例 1：一句话对话

```java
List<Map<String, Object>> messages = new ArrayList<>();
messages.add(ChatMessages.user("用一句话介绍幻塔"));

ChatResult r = openAiChatClient.complete(
        ChatRequest.builder().messages(messages).build());   // temperature 默认 1.0

String reply = r.isError() ? null : r.content();
```

## 例 2：多轮对话（你自己维护 messages）

模型不记账，每轮都要把完整历史发过去：

```java
List<Map<String, Object>> messages = new ArrayList<>();
messages.add(ChatMessages.system("你是助手"));
messages.add(ChatMessages.user("我叫小明"));

ChatResult r1 = openAiChatClient.complete(ChatRequest.builder().messages(messages).build());
messages.add(ChatMessages.assistant(r1.content())); // 把回复加回去

messages.add(ChatMessages.user("我叫什么？"));
ChatResult r2 = openAiChatClient.complete(ChatRequest.builder().messages(messages).build());
```

## 例 3：视觉（让模型看图）

`ChatMessages.userWithImage` 自动把 base64 拼成 data URL：

```java
List<Map<String, Object>> messages = new ArrayList<>();
messages.add(ChatMessages.userWithImage("这张图里是什么？", mimeType, base64Data)); // base64 不带前缀

ChatResult r = openAiChatClient.complete(
        ChatRequest.builder().messages(messages).timeoutSeconds(120).build()); // 图大，超时给足
```
> 模型须支持视觉（gpt-4o / claude-3.x 等）。

## 例 4：function calling（工具调用）

给 `tools`，从 `result.message()` 里取 `tool_calls`：

```java
List<Map<String, Object>> tools = List.of(
        ChatTools.function("get_weather", "查询某城市天气",
                Map.of("type", "object",
                       "properties", Map.of("city", Map.of("type", "string")),
                       "required", List.of("city"))));

ChatResult r = openAiChatClient.complete(
        ChatRequest.builder()
                .messages(messages)
                .tools(tools)
                .toolChoice("auto")   // 让模型自己决定；强制选用 "required"
                .temperature(0.2)     // 工具调用建议低温
                .build());

@SuppressWarnings("unchecked")
List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) r.message().get("tool_calls");
// 没有 tool_calls 说明模型直接给了 content（最终答案）
```
> 完整的「反复调工具直到结束」循环已经在 `agent/core/AbstractAgent` 里实现好了，agent 场景直接复用它，不用自己写循环。

## 例 5：流式（SSE / 打字机）

`stream()` 把每个增量片段交给回调；它**会抛异常**，自己 try/catch：

```java
StringBuilder full = new StringBuilder();
try {
    openAiChatClient.stream(
            ChatRequest.builder().messages(messages).build(),
            delta -> {
                full.append(delta);
                emitter.send(SseEmitter.event().data(Map.of("content", delta))); // 推给前端
            });
    emitter.complete();
} catch (Exception e) {
    emitter.completeWithError(e);
}
```

---

## 你不需要操心的

- **API key**：内部 `AiApiKeyProvider.pickTextKey()` 自动选可用 key、跳过被限流的。
- **429 限流**：命中后自动把该 key 封禁 86400s。
- **代理 / HttpClient**：用共享 bean，已按配置挂代理。
- **`Authorization` 头、解析 `choices`、content 是 String 还是数组**：都封装好了。

> 实战参考：`ai/service/impl/AiChatServiceImpl`（对话/识图/流式）、`agent/core/AbstractAgent` 与 `agent/router/AgentRouter`（工具调用）、`ocr/service/OcrTranslateImageProcessor`（批量翻译）。
