# ai.client —— AI 调用客户端层（开发者使用指南）

> 你要发一个 AI 请求时，**不要自己 new HttpClient、拼 key、写重试**。直接注入这一层封装好的客户端，
> 只管「我要说什么」和「拿到什么结果」。HTTP、代理、API key 选取、429 限流降级、请求体拼装、
> 响应解析，全在这一层处理好了。

## 我该用哪个？

| 我想做的事 | 用这个 | 方法 |
|---|---|---|
| 文本对话 / 多轮对话 | `openai.OpenAiChatClient` | `complete(ChatRequest)` |
| 让模型看图片并回答（视觉） | `openai.OpenAiChatClient` | `complete(ChatRequest)`（user content 放 image_url） |
| 工具调用 / function calling | `openai.OpenAiChatClient` | `complete(ChatRequest)`（带 `tools`） |
| 流式输出（SSE / 打字机效果） | `openai.OpenAiChatClient` | `stream(ChatRequest, onDelta)` |
| 生成图片 | `gemini.GeminiImageClient` | `generateImage(prompt, 参考图, mime)` |

- OpenAI 系（对话/视觉/工具/流式）→ 看 [`openai/README.md`](openai/README.md)
- Gemini 生图 → 看 [`gemini/README.md`](gemini/README.md)

## 怎么拿到客户端

它们都是 Spring `@Component`，构造注入即可：

```java
@Service
public class MyService {
    private final OpenAiChatClient openAiChatClient;   // com.hollow.build.ai.client.openai
    private final GeminiImageClient geminiImageClient; // com.hollow.build.ai.client.gemini

    public MyService(OpenAiChatClient openAiChatClient, GeminiImageClient geminiImageClient) {
        this.openAiChatClient = openAiChatClient;
        this.geminiImageClient = geminiImageClient;
    }
}
```

## 这一层替你扛了什么

| 文件 | 替你做的事 | 你要不要管 |
|---|---|---|
| `AiHttpClientConfig` | 提供唯一共享的 `HttpClient`（40s / HTTP_2 / 按配置挂代理） | 不用，客户端已注入 |
| `AiApiKeyProvider` | 从 key 列表里挑一个没被限流的；命中 429 自动把该 key 封禁 86400s | 不用，客户端内部已调用 |
| `OpenAiChatClient` / `GeminiImageClient` | 拼请求体、判错、解析、提取内容 | 你只给输入、读结果 |

**所以**：你不需要写 `Bearer` 头、不需要判断哪个 key 还能用、不需要处理代理、不需要解析 `choices[0].message.content`。

## 两条要记住的约定

1. **`complete()` / `generateImage()` 不抛异常**。失败信息在结果对象的 `errorMessage()`，先判 `isError()` 再用。
   （`stream()` 例外：它会把异常抛给你，方便 SSE 那边 `completeWithError`。）
2. **key 全被限流时**返回带 `errorMessage` 的失败结果，而不是用 `Bearer null` 去打上游。

## 相关配置（`application.yml` → `com.hollow.ai`）

```yaml
com:
  hollow:
    ai:
      text-uri:  https://.../v1/chat/completions   # OpenAI 兼容地址
      text-model: gpt-4o
      text-api-key: [key1, key2]                    # 列表，轮询 + 限流降级
      text-default-prompt: ""                       # 可选 system prompt
      image-uri:  https://.../v1beta/models/        # Gemini，客户端会拼 model + ":generateContent"
      image-model: gemini-2.5-flash-image
      image-api-key: [keyA, keyB]
      proxy-enabled: false
      proxy-address: 127.0.0.1
      proxy-port: 7890
```

字段绑定在 `ai/config/AiConfigurationProperties.java`。
