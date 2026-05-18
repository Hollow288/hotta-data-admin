# `/api/v1/agent/ask` × 别名 Agent 输入输出说明

本文只关心一种链路：

```
用户调 /ask
  ↓
Router 调用 route_to_alias
  ↓
AliasAgent 解析别名
  ↓
返回稳定 AgentResponse
```

其它链路见 `src/main/java/com/hollow/build/agent/README.md`。

---

## 1. 接口基本信息

| 项 | 值 |
|---|---|
| 方法 | `POST` |
| 路径 | `/api/v1/agent/ask` |
| 调试路径 | `/api/v1/agent/ask?debug=true` |
| 请求 Content-Type | `application/json` |
| 鉴权 | 无（`@PublicEndpoint`，但仍受 API Key 限流过滤影响） |

请求体：

```json
{ "message": "<用户的自然语言提问>" }
```

---

## 2. 当前响应结构

默认响应：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "requestId": "7c4d...",
    "agent": "alias",
    "answerText": "赤风",
    "answerData": {
      "type": "武器",
      "value": "赤风"
    }
  }
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `requestId` | 本次请求 ID，可用于查 `agent_request_log` / `agent_ai_call_log` |
| `agent` | 实际处理请求的 agent，这里应为 `alias` |
| `answerText` | 给用户展示的文本。命中时通常是正式名；未命中时是原因 |
| `answerData` | 给程序使用的结构化结果，不再需要客户端二次 `JSON.parse` |
| `debugTrace` | 仅 `?debug=true` 时返回，包含工具调用轨迹 |

调试响应会多一个 `debugTrace`：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "requestId": "7c4d...",
    "agent": "alias",
    "answerText": "赤风",
    "answerData": {
      "type": "武器",
      "value": "赤风"
    },
    "debugTrace": [
      "list_categories({}) -> {\"categories\":[\"武器\",\"意志\",\"源器\"]}",
      "search_alias({\"category\":\"武器\",\"query\":\"大红莲\"}) -> {...}"
    ]
  }
}
```

---

## 3. 完整业务流程

以 `"帮我查查大红莲的武器信息"` 为例：

```
Router:
  route_to_alias({"confidence":0.9,"reason":"用户提到武器别名"})

AliasAgent:
  1. list_categories()
  2. 从用户问题里读出 type=武器
  3. search_alias(category="武器", query="大红莲")
  4. 模型输出 {"type":"武器","value":"赤风"}
  5. 后端把这个 JSON 字符串解析成 answerData

返回:
  answerText = "赤风"
  answerData = {"type":"武器","value":"赤风"}
```

---

## 4. 输入场景

| # | 输入形态 | 示例 `message` | 行为 |
|---|---|---|---|
| 1 | 别名 + 明确类型 | `"大红莲的武器信息"` | type=武器, query=大红莲，命中赤风 |
| 2 | 正式名 + 明确类型 | `"赤风是哪个武器"` | exact_canonical 命中赤风 |
| 3 | 别名 + 类型隐含 | `"三刀哥是什么"` | 按武器 -> 意志 -> 源器试探，通常命中意志：演绎 |
| 4 | 错别字 / 变体 | `"赤峰的武器"`、`"凌波零"` | fuzzy 规则兜底 |
| 5 | 别名跨多类型 | `"大红莲是什么"` | 可能命中多个分类，按系统提示词挑一个 |
| 6 | 完全没意义 | `"查查查查"`、`"hi"` | AliasAgent 正常完成，但 answerData.type/value 为 null |
| 7 | 多个别名一起问 | `"大红莲和裂空"` | 行为不保证稳定，建议拆成多次请求 |

---

## 5. 输出场景

### 5.1 命中

```json
{
  "answerText": "赤风",
  "answerData": {
    "type": "武器",
    "value": "赤风"
  }
}
```

`type` 只应是：

```text
武器 / 意志 / 源器
```

### 5.2 未命中

```json
{
  "answerText": "未在别名库中找到匹配项",
  "answerData": {
    "type": null,
    "value": null,
    "reason": "未在别名库中找到匹配项"
  }
}
```

客户端判断未命中时，不要按 `answerText` 文案匹配，直接判断：

```javascript
res.data.answerData?.type === null
```

### 5.3 不属于任何 agent

如果用户问的是当前系统没有的能力，例如：

```json
{ "message": "帮我生成一张图片" }
```

Router 会调用 `route_to_unsupported`，返回：

```json
{
  "code": 400,
  "msg": "当前 agent 系统暂不支持这个问题: 当前没有图片生成类 agent"
}
```

---

## 6. 接口层 code

| `code` | 含义 | 触发场景 |
|---|---|---|
| `200` | 业务成功 | AliasAgent 正常跑完；命中和未命中都算成功 |
| `400` | 当前不支持 | Router 调用 `route_to_unsupported`，或所有 agent 都关闭 |
| `501` | agent 已关闭 | 直连 `/alias` 但 alias 被 admin 开关关闭 |
| `500` | 系统异常 | LLM 调用失败、Router 协议异常、未知异常等 |

---

## 7. 客户端推荐处理逻辑

```javascript
const res = await fetch('/api/v1/agent/ask', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-API-KEY': apiKey
  },
  body: JSON.stringify({ message: userInput })
}).then(r => r.json());

if (res.code !== 200) {
  return showError(res.msg);
}

const data = res.data.answerData;
if (data?.type === null) {
  showTip('没找到这个别名对应的物品');
} else {
  showItem(data.type, data.value);
}
```

---

## 8. 易踩坑

1. **`answerData` 已经是对象**，客户端不要再 `JSON.parse`。
2. **未命中仍然是 200**，因为 AliasAgent 正常完成了别名检索流程。
3. **不支持的问题是 400**，因为 Router 明确调用了 `route_to_unsupported`。
4. **`debugTrace` 只在 `?debug=true` 时返回**，普通业务接口不要依赖它。
5. **多别名混合查询行为未定义**，交互上建议一次只问一个别名。
