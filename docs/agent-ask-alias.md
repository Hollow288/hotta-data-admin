# `/api/v1/agent/ask` × 别名 Agent —— 输入输出场景汇总

> 本文只关心一种链路：用户调 `/ask` → Router 派给 **AliasAgent**。其它链路（数据库 agent、直连入口）请看 README。

---

## 1. 接口基本信息

| 项 | 值 |
|---|---|
| 方法 | `POST` |
| 路径 | `/api/v1/agent/ask` |
| 请求 Content-Type | `application/json` |
| 鉴权 | 无（`@PublicEndpoint`）|

**请求体**

```json
{ "message": "<用户的自然语言提问>" }
```

只有一个字段 `message`，纯字符串。

**响应体**（统一 `ApiResponse` 包装）

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "reply": "<AI 给出的最终回复字符串>",
    "trace": [
      "list_categories({}) -> {...}",
      "search_alias({\"category\":\"武器\",\"query\":\"大红莲\"}) -> {...}"
    ]
  }
}
```

- `data.reply` —— **AI 给的最终回复**，按 AliasAgent 的系统提示词约定，**必须是一个 JSON 对象的字符串**（注意：是字符串，不是 JSON 对象；客户端需要再 `JSON.parse` 一次）。
- `data.trace` —— Agent 这一次 ask 调过哪些工具、入参出参，便于调试。生产环境可以裁掉。

---

## 2. 完整业务流程（用户视角）

```
用户: "帮我查查大红莲的武器信息"
   ↓
Router (1 次 LLM 调用): 判定 → alias
   ↓
AliasAgent 主循环:
  ① list_categories()                     → ["武器","意志","源器"]
  ② AI 从用户问题里读出 type="武器"
  ③ search_alias(category="武器", query="大红莲")
       → {"matches":[{"canonical":"赤风","matchedBy":"exact_alias"}]}
  ④ AI 输出最终 JSON
   ↓
返回: data.reply = '{"type":"武器","value":"赤风"}'
```

---

## 3. 输入场景（`message` 字段的形态）

> **共性**：AliasAgent 系统提示词允许 AI **根据上下文猜测用户想问的别名**（容错错别字、口语化、字形相似）。AI 会尽量从 alias.json 的 `aliases` 列表里找匹配。

| # | 输入形态 | 示例 `message` | AI 内部判定 |
|---|---|---|---|
| 1 | 别名 + 明确类型 | `"大红莲的武器信息"` | type=武器, query=大红莲 → 命中 赤风 |
| 2 | 正式名 + 明确类型 | `"赤风是哪个武器"` | type=武器, query=赤风 → exact_canonical 命中 |
| 3 | 别名 + 类型隐含 | `"三刀哥是什么"` | AI 没明说类型，按"武器→意志→源器"顺序试，第二次试中意志：演绎 |
| 4 | 错别字 / 变体 | `"赤峰的武器"`、`"凌波零"` | AI 容错猜成 "赤风"/"救赎" → 命中 |
| 5 | 别名跨多类型 | `"大红莲是什么"` | 武器→赤风、意志→烈烈红莲，AI 按系统提示词只挑第一个（武器）|
| 6 | 完全没意义 | `"查查查查"`、`"hi"` | 走流程后所有分类都未命中 → 输出"未找到" |
| 7 | 多个别名一起问 | `"大红莲和裂空"` | **不保证准确**——AI 会随机挑一个回，建议拆成两次问 |

> 输入 1~5 通常都能正常命中；6 走"未找到"分支；7 是行为未定义场景。

---

## 4. 输出场景（`data.reply` 内容）

按 `AliasAgent.systemPrompt()` 约定，AI 的最终回复**必须是一个 JSON 对象**（且不带 markdown、不带前后缀）。两种正常情况：

### 4.1 命中 —— 返回 type + value

```jsonc
// data.reply 解析后：
{
  "type": "武器",            // 一定是 "武器" | "意志" | "源器"
  "value": "赤风"            // alias.json 里的正式名
}
```

例：

| 用户输入 | reply |
|---|---|
| `"大红莲的武器信息"` | `{"type":"武器","value":"赤风"}` |
| `"三刀哥是什么意志"` | `{"type":"意志","value":"演绎"}` |
| `"考二的源器"`       | `{"type":"源器","value":"考恩特Ⅱ型"}` |
| `"赤风"`             | `{"type":"武器","value":"赤风"}` |

### 4.2 未命中 —— 返回 null + reason

```jsonc
{
  "type": null,
  "value": null,
  "reason": "未在别名库中找到匹配项"
}
```

触发条件：alias.json 的三类（武器/意志/源器）都没找到 query 对应的项。

**注意**：`reason` 文字是 AI 写的，不是固定串。客户端不要按字面文案匹配，**只判定 `type === null` 就当作未命中**。

---

## 5. 接口层 `code` 全景

`data.reply` 是 AI 的内容，而 `code` 是 HTTP / 业务层的状态。两者要分开看。

| `code` | 含义 | 触发场景 |
|---|---|---|
| `200` | 业务成功 | AliasAgent 正常跑完（命中 / 未命中**都算成功**，差别在 reply 内容里）|
| `501` | `agent 已关闭: alias` | 通过 admin 接口把 alias 关掉后，再调 `/ask` 且路由到 alias 时 |
| `500` | `系统异常` | LLM 接口报错、超时、未知异常等 |

> **重点提醒**：未命中是 `code=200`，**不是** 4xx。Alias agent 跑完它的工作流就是成功，"找不到"只是业务结果之一。

---

## 6. 完整请求 / 响应示例

### 6.1 命中（典型成功）

请求：
```http
POST /api/v1/agent/ask
Content-Type: application/json

{"message":"帮我查查大红莲的武器信息"}
```

响应：
```jsonc
{
  "code": 200,
  "msg": "成功",
  "data": {
    "reply": "{\"type\":\"武器\",\"value\":\"赤风\"}",
    "trace": [
      "list_categories({}) -> {\"categories\":[\"武器\",\"意志\",\"源器\"]}",
      "search_alias({\"category\":\"武器\",\"query\":\"大红莲\"}) -> {\"category\":\"武器\",\"query\":\"大红莲\",\"matches\":[{\"canonical\":\"赤风\",\"matchedBy\":\"exact_alias\"}]}"
    ]
  }
}
```

### 6.2 类型隐含 + 跨分类回退

请求：`{"message":"三刀哥是什么"}`

响应（trace 会更长，AI 第一次试武器没中，再试意志中了）：
```jsonc
{
  "code": 200,
  "msg": "成功",
  "data": {
    "reply": "{\"type\":\"意志\",\"value\":\"演绎\"}",
    "trace": [
      "list_categories({}) -> {\"categories\":[\"武器\",\"意志\",\"源器\"]}",
      "search_alias({\"category\":\"武器\",\"query\":\"三刀哥\"}) -> {\"matches\":[]}",
      "search_alias({\"category\":\"意志\",\"query\":\"三刀哥\"}) -> {\"matches\":[{\"canonical\":\"演绎\",\"matchedBy\":\"exact_alias\"}]}"
    ]
  }
}
```

### 6.3 未命中

请求：`{"message":"查查查查"}`

响应：
```jsonc
{
  "code": 200,
  "msg": "成功",
  "data": {
    "reply": "{\"type\":null,\"value\":null,\"reason\":\"未在别名库中找到匹配项\"}",
    "trace": [
      "list_categories({}) -> {...}",
      "search_alias({\"category\":\"武器\",\"query\":\"查查查查\"}) -> {\"matches\":[]}",
      "search_alias({\"category\":\"意志\",\"query\":\"查查查查\"}) -> {\"matches\":[]}",
      "search_alias({\"category\":\"源器\",\"query\":\"查查查查\"}) -> {\"matches\":[]}"
    ]
  }
}
```

### 6.4 alias 被运营开关关闭

请求：`{"message":"大红莲的武器"}`（当时 alias 被关、但 router 选中了它）

响应：
```jsonc
{
  "code": 501,
  "msg": "agent 已关闭: alias"
  // 没有 data
}
```

### 6.5 LLM 调用失败 / 未知系统异常

```jsonc
{
  "code": 500,
  "msg": "系统异常"
}
```

实际原因会写进服务端日志和 `agent_ai_call_log.error_message`，客户端只看到统一的"系统异常"。

---

## 7. 客户端推荐处理逻辑

```javascript
const res = await fetch('/api/v1/agent/ask', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({message: userInput})
}).then(r => r.json());

if (res.code !== 200) {
  // 501 = agent 关了；500 = 系统异常
  return showError(res.msg);
}

// AI 的最终输出是个 JSON 字符串，需要再 parse 一次
const aiResult = JSON.parse(res.data.reply);

if (aiResult.type === null) {
  // 未命中
  showTip('没找到这个别名对应的物品');
} else {
  // 命中
  showItem(aiResult.type, aiResult.value);
}
```

---

## 8. 易踩坑

1. **`reply` 是字符串不是对象**——AI 输出整体被当成普通文本返回，客户端要 `JSON.parse` 一次再用。
2. **未命中是 200 不是 404**——别用 HTTP 状态码判断是否找到；用 `data.reply` 解析后的 `type` 字段。
3. **AI 偶尔会输出多余文字**——尽管系统提示词强约束"只输出 JSON 不要前后缀"，但模型偶尔会带半行解释。客户端解析失败时可以兜底用正则提取 `{...}`。
4. **多别名混合查询行为未定义**——`"大红莲和裂空"` 这种问法，AI 大概率只解析其中一个。业务侧应该在交互上引导用户一次只问一个。
5. **trace 是调试用的别上线展示**——里面可能有内部表/字段名，给前端用户看不友好；建议生产环境的 controller 把 trace 过滤掉。
6. **路由可能派错**——如果 alias agent 被运营关掉、用户又问的是别名问题，Router 会 fallback 到第一个开启的 agent（可能是 database），那它必然答不上来。从代码层面是合理行为，但产品上要考虑给用户友好提示。
