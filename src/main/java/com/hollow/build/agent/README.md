# Agent 教学示例

> 让 AI 自己决定调哪些工具去查数据库——一个最小、可跑通的 Agent。

---

## 1. 先把几个名词分清楚

平时听到的 **Tool / Agent / Skill / MCP** 经常被混着说，其实它们处在不同层次：

| 名词     | 定位                           | 它解决的问题                            | 在本项目里的对应物                          |
|--------|------------------------------|----------------------------------|-----------------------------------|
| Tool   | 一个函数                         | 让 AI 能调用真实代码（查库、发邮件、读文件……）       | `tool/` 目录下的三个 `Tool` 实现           |
| Agent  | 一个"循环"：让 AI 反复决策直到完成任务        | 把"调工具→看结果→再决定"自动化                 | `core/DatabaseAgent.java`         |
| Skill  | 一组预设的提示词 + 工作流（不一定带工具）       | 给 AI 一种"职业技能模板"，例如"代码评审员""日程助理"  | 这个项目里没写，可以理解为 system prompt + 工具集合 |
| MCP    | 一种**协议**（Model Context Protocol） | 把工具/资源/提示词标准化暴露出去，让任意 AI 客户端能接入  | 本项目没用 MCP；如果要做，相当于把 `ToolRegistry` 用 MCP 协议对外暴露 |

一句话总结：

```
Tool   = 一个函数
Agent  = 调工具的 for 循环
Skill  = 一份给 AI 的"角色说明书"
MCP    = 把工具暴露出去的统一协议
```

本示例是一个**带 3 个 Tool 的 Agent**。Skill 和 MCP 在这里都没必要做，理解概念即可。

---

## 2. 目录结构

```
agent/
├── README.md                       <- 你正在读的这份
├── config/
│   └── AgentProperties.java        <- 白名单/最大迭代次数等配置
├── tool/
│   ├── Tool.java                   <- "什么是一个工具" 的接口
│   ├── ToolRegistry.java           <- 收集所有工具，转 OpenAI 格式 / 调用工具
│   └── impl/
│       ├── ListTablesTool.java     <- 列出可访问的表
│       ├── DescribeTableTool.java  <- 看某张表有哪些字段
│       └── QueryTableTool.java     <- 查表数据（受限）
├── core/
│   ├── AgentAiClient.java          <- 真正发 HTTP 请求给 AI 的客户端（带 tools 字段）
│   └── DatabaseAgent.java          <- Agent 主循环
├── dto/
│   ├── AgentRequest.java
│   └── AgentResponse.java
└── controller/
    └── AgentController.java        <- POST /api/v1/agent/ask
```

---

## 3. Agent 是怎么跑的（最重要）

整个流程其实就是一个 `for` 循环。看 `DatabaseAgent.ask()`：

```
┌──────────────────────────────────────────────────────────┐
│ messages = [system 提示, user 问题]                        │
│                                                          │
│ for i in 1..maxIterations:                               │
│     aiMessage = AI(messages, tools)        ← 把工具列表也发给 AI │
│                                                          │
│     if aiMessage.tool_calls 为空:                          │
│         return aiMessage.content           ← AI 给最终答了    │
│                                                          │
│     messages += aiMessage                  ← 把 AI 的话原样塞回 │
│     for call in aiMessage.tool_calls:                    │
│         result = 执行(call.function.name, call.arguments)  │
│         messages += { role:tool, content:result }        │
│ end for                                                  │
└──────────────────────────────────────────────────────────┘
```

一次真实对话长这样：

```
user:      "blog_posts 表里最新发布的 3 篇是什么？"
↓ AI 看到 tools 里有 list_tables / describe_table / query_table

assistant: tool_calls=[ describe_table(table="blog_posts") ]
tool:      {"columns":[ {"name":"blog_id"...}, {"name":"created_at"...} ]}
↓ AI 看完字段，知道按 created_at 排序

assistant: tool_calls=[ query_table(table="blog_posts", limit=3 ...) ]
tool:      {"rows":[ ... ]}
↓ AI 拿到数据

assistant: content="最新 3 篇是 …"           ← 没有 tool_calls 就结束循环
```

**关键点**：

- AI 不会自己执行任何 SQL，它只是"动嘴"说"我想调 query_table，参数是这些"。
- 真正执行 SQL 的是 Java 代码。**安全护栏永远写在 Java 里，不要寄希望于 prompt**。
- 每一轮 messages 都要把上一轮 AI 说的话和工具结果原样带上，AI 才知道前因后果。

---

## 4. 给 AI 的"工具说明书"长什么样

`ToolRegistry.openAiFormat()` 会把每个 Tool 转成这种结构：

```json
{
  "type": "function",
  "function": {
    "name": "query_table",
    "description": "查询某张表的数据。可指定一个等值过滤条件...",
    "parameters": {
      "type": "object",
      "properties": {
        "table":      { "type": "string", "description": "..." },
        "whereField": { "type": "string", "description": "..." },
        "whereValue": { "type": "string", "description": "..." },
        "limit":      { "type": "integer", "description": "..." }
      },
      "required": ["table"]
    }
  }
}
```

这是 OpenAI 定义、几乎所有主流模型（DeepSeek / Qwen / Doubao / Kimi / GLM）都兼容的格式。

`description` 写得越好，AI 调用得越准——把它当成"给同事写文档"来对待。

---

## 5. 安全护栏（重要！）

把 AI 接到数据库是有风险的，任何"AI 决定"都不能直接信任。本示例的护栏：

1. **表白名单**：`AgentProperties.allowedTables`。不在白名单的表，三个工具都会拒绝。
2. **不让 AI 写裸 SQL**：`QueryTableTool` 只接受结构化参数（表名、过滤列、过滤值、limit）。
3. **列名正则校验**：`whereField` 必须匹配 `[A-Za-z_][A-Za-z0-9_]*`，避免拼到 SQL 里被注入。
4. **过滤值参数化**：`whereValue` 通过 `PreparedStatement` 绑定，不参与字符串拼接。
5. **行数上限**：`limit` 被钳制到 `[1, maxQueryLimit]`。
6. **迭代次数上限**：`maxIterations` 防止 AI 死循环调工具。

如果未来想让 AI 直接写 SQL（更灵活但更危险），需要加：只读账号、SELECT 白名单解析、超时熔断、行数硬上限……

---

## 6. 怎么试

### 配置（已加好）

`application.yml`：

```yaml
com:
  hollow:
    agent:
      max-iterations: 6
      max-query-limit: 50
      allowed-tables:
        - blog_posts
        - event_news
        - role
```

AI 模型走的是项目原本的 `com.hollow.ai.text-*` 那一套配置，不用改。

### 启动后调用

```bash
curl -X POST http://localhost:5777/api/v1/agent/ask \
  -H "Content-Type: application/json" \
  -d '{"message": "blog_posts 表里最新的 3 篇文章标题和创建时间是什么？"}'
```

返回示例：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "reply": "最新的 3 篇文章是：……",
    "trace": [
      "describe_table({\"table\":\"blog_posts\"}) -> {...}",
      "query_table({\"table\":\"blog_posts\",\"limit\":3}) -> {...}"
    ]
  }
}
```

`trace` 字段是教学/调试用的——它把 AI 这一轮里调过的每个工具和返回内容都打出来了，你能直接看到 AI 是怎么"思考"的。

---

## 7. 自己加一个工具——只要 3 步

比如加一个 `count_rows` 工具：

1. 在 `tool/impl/` 下新建 `CountRowsTool implements Tool`
2. 加上 `@Component`
3. 实现 `name() / description() / parametersSchema() / execute()` 四个方法

完事。`ToolRegistry` 会自动收集，AI 下一次请求就能看见它，无需改 Agent 主循环。

这就是这种结构的好处：**Agent 主循环不变，扩展能力 = 加 Tool**。

---

## 8. 跟 Spring AI / LangChain4j / Anthropic SDK 的关系

这些框架做的事情**就是**这个示例做的事——只是封装得更厚、支持更多模型、加了流式/缓存/重试/并发工具调用等等。

理解了本示例，再去看那些框架的 `ChatClient.tools(...)` 或 `AgentExecutor`，会发现完全是同一个循环。所以**先把最小内核跑通，再决定要不要换框架**。
