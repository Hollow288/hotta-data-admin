# GeminiImageClient 使用指南

谷歌 Gemini 生图（`generateContent`）封装。支持纯文生图，也支持带参考图。

```java
private final GeminiImageClient geminiImageClient; // 构造注入即可
```

唯一方法：

```java
GeminiImageResult generateImage(String prompt, String refImageBase64, String refImageMimeType);
```

| 入参 | 说明 |
|---|---|
| `prompt` | 文本提示。**直接传原文**，不要自己做 JSON 转义（内部用 fastjson2 统一转义） |
| `refImageBase64` | 可选参考图，纯 base64（不带 `data:` 前缀）；纯文生图传 `null` |
| `refImageMimeType` | 参考图 MIME（如 `image/png`），仅当有参考图时用 |

---

## GeminiImageResult：你能拿到什么

`generateImage()` 返回的 record，**先判 `isError()`**：

| 字段 | 说明 |
|---|---|
| `isError()` | 失败返回 true |
| `errorMessage()` | 失败原因（上游错误 / finishReason 非 STOP / 无可用图像 key / 异常） |
| `data()` | 成功时的图片 base64 |
| `mimeType()` | 成功时的图片类型 |
| `finishReason()` | 上游结束原因 |

---

## 例 1：纯文生图

```java
GeminiImageResult r = geminiImageClient.generateImage(
        "画一只在窗台晒太阳的橘猫，扁平插画风", null, null);

if (r.isError()) {
    return ApiResponse.fail(r.errorMessage());
}
String base64 = r.data();
String mime   = r.mimeType();
```

## 例 2：带参考图（图生图）

```java
GeminiImageResult r = geminiImageClient.generateImage(
        "把这张图改成赛博朋克夜景风格",
        refBase64,        // 纯 base64
        "image/png");
```

---

## 你不需要操心的

- **图像 API key**：内部 `AiApiKeyProvider.pickImageKey()` 自动选可用 key；命中 429 自动封禁。
- **请求 URL**：自动拼成 `image-uri` + `image-model` + `:generateContent`。
- **鉴权头**：自动加 `x-goog-api-key`。
- **请求体结构**：`contents/parts/inline_data/generationConfig` 都拼好了，固定 `aspectRatio = 9:16`。
- **JSON 转义**：prompt 原样传入即可，内部统一转义（早期「手工转义 + 再序列化」导致换行/引号变字面量的问题已修复）。

> 需要改纵横比、`responseModalities` 等生成参数，去 `GeminiImageClient.buildJsonBody()` 调整。
> 实战参考：`ai/service/impl/AiChatServiceImpl.image()`。
