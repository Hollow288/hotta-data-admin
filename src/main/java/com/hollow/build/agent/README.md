# Agent 教学示例（多 Agent + 路由）

> 一个最小但**完整**的多 Agent 系统：用户输入一句话，**Router** 自动判断该派给谁，对应的 **Agent** 在自己专属的工具集里来回决策、调工具，最后给出回复。全程异步落库可追溯。
>
> 看完这个 README，你应该能把 "Tool / Agent / Router / Skill / MCP" 这几个名词的关系理清，并且能照葫芦画瓢加一个新 Agent。

---

## 1. 名词速通

| 名词     | 一句话定义                                  | 在本项目里的化身                          |
|----------|---------------------------------------------|-------------------------------------------|
| **Tool**     | AI 能调用的一个 Java 函数                  | `database/tool/`、`alias/tool/` 下的实现  |
| **Agent**    | "调工具的 for 循环"——AI 反复决策直到完成 | `DatabaseAgent`、`AliasAgent`            |
| **Router**   | 一次轻量 LLM 判定，决定派给哪个 Agent      | `router/AgentRouter`                      |
| **Skill**    | 一份给 AI 的"角色说明书"（提示词 + 流程）  | 每个 Agent 的 `systemPrompt()` 就是它的 skill |
| **MCP**      | 把工具按统一协议暴露出去的标准（外部协议） | 本项目没用                                |

> **Tool ⊂ Agent ⊂ 多 Agent 系统（带 Router 编排）。** 一层比一层"宏观"。

---

## 2. 目录长这样

```
agent/
├─ core/                       ← 框架基础件，跟具体业务无关
│   ├─ Tool.java                  - 工具接口（任何工具实现它）
│   ├─ ToolRegistry.java          - 把一组工具打包给 AI 用
│   ├─ AbstractAgent.java         - "调工具循环"的通用实现
│   ├─ AgentAiClient.java         - 真正发 HTTP 调大模型的客户端
│   └─ AiCallOutcome.java         - 一次 AI 调用的完整结果（含元数据）
│
├─ database/                   ← 数据库 Agent 模块（自包含）
│   ├─ DatabaseAgent.java         - 继承 AbstractAgent，写 systemPrompt
│   ├─ DatabaseTool.java          - 标记接口（让 Spring 区分"哪些工具属于 db"）
│   └─ tool/
│       ├─ ListTablesTool.java    - 列出可访问表
│       ├─ DescribeTableTool.java - 看表结构
│       └─ QueryTableTool.java    - 查数据（不让 AI 写 SQL，只填模板）
│
├─ alias/                      ← 别名 Agent 模块（自包含）
│   ├─ AliasAgent.java            - 继承 AbstractAgent
│   ├─ AliasTool.java             - 标记接口
│   ├─ AliasDataLoader.java       - 启动时把 alias.json 加载到内存
│   └─ tool/
│       ├─ ListCategoriesTool.java - 列出武器/意志/源器
│       └─ SearchAliasTool.java    - 给个别名查正式名
│
├─ router/                     ← 编排层
│   ├─ AgentRouter.java           - 用 LLM 判定 → 派发给对应 Agent
│   └─ RouteDecision.java         - 路由判定的结果记录
│
├─ controller/                 ← HTTP 入口
│   ├─ AgentController.java       - /api/v1/agent/{ask|database|alias}
│   └─ AgentAdminController.java  - /api/v1/agent/admin/switches（开关管理）
│
├─ config/                     ← 装配
│   ├─ AgentProperties.java       - 配置项绑定（迭代上限、表白名单、默认开关等）
│   ├─ AgentToolRegistries.java   - 给每个 Agent 装配独立的 ToolRegistry
│   ├─ AgentSwitches.java         - 运行时 agent 开关（内存态）
│   └─ AgentDisabledException.java - agent 被关时抛此异常，由全局处理器转 501
│
├─ dto/                        ← HTTP 入参/出参
├─ entity/                     ← 日志表实体（agent_request_log / agent_ai_call_log）
├─ repository/                 ← MyBatis Mapper
├─ log/                        ← AgentLogService（异步写日志）
└─ README.md                   ← 本文件
```

**自包含原则**：每个 `xxx/` 包就是一个完整模块。要看 AliasAgent 怎么实现的，只用打开 `alias/`，**不用跨目录跳来跳去**。

---

## 3. 一次完整请求的"流水线"（重点！）

以用户发 `POST /api/v1/agent/ask {"message":"帮我查查大红莲的武器信息"}` 为例：

```
用户 HTTP 请求
    │
    ▼
┌─────────────────────────────────────────────┐
│ AgentController.ask()                       │
│  - 生成 requestId（贯穿全链路的"追踪 ID"）   │
│  - 取客户端 IP                              │
│  - 调 AgentRouter.route(...)                │
└─────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────┐
│ AgentRouter.route()                         │
│  ① decide() —— 一次 LLM 调用做"分类"        │
│       AgentAiClient.complete(messages, [])  │  ← 不带 tools
│       prompt 里要求只输出 JSON              │
│       → AgentLogService.saveAiCallLog       │  ← 落明细表，agent_name="router"
│       → 解析得 {target:"alias",confidence}  │
│  ② switch 派发到 AliasAgent                 │
└─────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────┐
│ AliasAgent.ask()  ← 走 AbstractAgent 的循环 │
│                                             │
│  messages = [system, user]                  │
│  for i in 0..maxIterations:                 │
│    ┌─ AgentAiClient.complete(messages,tools)│
│    │   → AI 返回 message                    │
│    │   → saveAiCallLog (agent_name="alias") │
│    ├─ 若 message 含 tool_calls:             │
│    │     append assistant message           │
│    │     for each call:                     │
│    │       toolRegistry.invoke(name, args)  │  ← 真正跑 Java
│    │       append tool result message       │
│    │     continue 循环                      │
│    └─ 否则: 拿到最终 content, return        │
│                                             │
│  finally:                                   │
│    AgentLogService.saveRequestLog(...)      │  ← 落主表
└─────────────────────────────────────────────┘
    │
    ▼
返回 {reply: "{...}", trace: [...]}
```

**关键点**：

- 一次 ask 内会发 **N 次 HTTP 调用**给大模型。每次都把"全部对话历史"重新发过去（大模型自己不记账）。
- AI 决定何时停止——它不再返回 `tool_calls` 而是返回 `content` 时，循环就结束。
- Router 自己也是一次 AI 调用，只是它**不带 tools**，纯做分类。

---

## 4. 调工具循环里 `messages` 长什么样？

理解这个数组是看懂日志的关键。它**不断追加**，每轮发给 AI 的就是"截至目前的全部消息"：

```jsonc
// 第 1 次发给 AI 的 messages
[
  {role:"system",  content:"你是别名解析助手……"},
  {role:"user",    content:"帮我查查大红莲的武器信息"}
]
// AI 回复：tool_calls=[list_categories]，无 content

// 第 2 次发给 AI 的 messages（追加了 2 条）
[
  ...上面 2 条,
  {role:"assistant", tool_calls:[{id:"call_xxx", function:{name:"list_categories"}}]},
  {role:"tool",      tool_call_id:"call_xxx", name:"list_categories",
                     content:'{"categories":["武器","意志","源器"]}'}
]
// AI 回复：tool_calls=[search_alias{category:"武器", query:"大红莲"}]

// 第 3 次发给 AI 的 messages（再追加 2 条）
[
  ...上面 4 条,
  {role:"assistant", tool_calls:[{id:"call_yyy", function:{name:"search_alias", ...}}]},
  {role:"tool",      tool_call_id:"call_yyy", name:"search_alias",
                     content:'{"matches":[{"canonical":"赤风","matchedBy":"exact_alias"}]}'}
]
// AI 回复：content='{"type":"武器","value":"赤风"}'，无 tool_calls → 循环终止
```

**4 种 role**：

| role        | 谁产生 | 啥时候追加 |
|-------------|--------|-----------|
| `system`    | 我们写死 | ask 开始时 push 一次 |
| `user`      | 用户输入 | ask 开始时 push 一次 |
| `assistant` | AI 返回 | 每次 AI HTTP 调用 push 1 条 |
| `tool`      | 我们本地执行工具的结果 | AI 让你调几个就 push 几条 |

---

## 5. 数据库怎么设计的？为什么这么设计？

两张表，**主从结构**，用 `request_id` 软关联：

### `agent_request_log` —— 业务粒度（每次 ask 一行）

| 字段 | 说明 |
|------|------|
| `request_id` | 全链路追踪 ID（一次 ask 唯一） |
| `agent_name` | 哪个 agent 处理的（database / alias） |
| `user_message` | 用户原始问题 |
| `reply` | AI 给出的最终回复 |
| `trace` | 工具调用轨迹（JSON 数组：`[{tool, args, result, ms}]`）|
| `iterations` | 主循环跑了几轮 |
| `tool_call_count` | 总调了几次工具 |
| `total_tokens` | 整次 ask 累计 token |
| `status` | SUCCESS / ERROR / MAX_ITERATIONS |
| `duration_ms` | 总耗时 |

### `agent_ai_call_log` —— 调用粒度（每次 AI HTTP 一行）

| 字段 | 说明 |
|------|------|
| `request_id` | 同主表 |
| `agent_name` | router / database / alias |
| `iteration_index` | 在主循环里第几轮（从 0 开始） |
| `request_body` | 完整请求 JSON（含 messages + tools）|
| `response_body` | 完整响应 JSON |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | token 拆分 |
| `tool_call_count` | 本轮 AI 让你调了几个工具 |
| `finish_reason` | AI 给的停止原因 |
| `duration_ms` | 这一次 HTTP 耗时 |

### 怎么用这两张表学习

按一个 `request_id` 去 `agent_ai_call_log` 排序看，就能看到完整链路：

```
iteration | agent_name | 干了啥
0         | router     | 判定 target=alias
0         | alias      | AI 决定调 list_categories
1         | alias      | AI 决定调 search_alias
2         | alias      | AI 给出最终 JSON（无 tool_calls）
```

把每一行的 `request_body` 拿出来看 `messages` 数组——你会非常直观地看到它**像滚雪球一样越来越长**。

---

## 6. 多 Agent 的关键魔法：Spring "按类型分组注入"

这是这个项目最值得学的工程技巧。看 `AgentToolRegistries.java`：

```java
@Bean
public ToolRegistry databaseToolRegistry(List<DatabaseTool> tools) {
    return new ToolRegistry(tools);   // 只拿到所有 DatabaseTool
}

@Bean
public ToolRegistry aliasToolRegistry(List<AliasTool> tools) {
    return new ToolRegistry(tools);   // 只拿到所有 AliasTool
}
```

魔法就在 `List<DatabaseTool>`：Spring 会把容器里**所有实现了 DatabaseTool 接口的 Bean** 都收集进来，**不会**把 AliasTool 混进来。

所以：

- `DatabaseTool` / `AliasTool` 是**标记接口**（marker interface）——什么方法都不加，只用来"打标签"。
- 工具实现写 `class ListTablesTool implements DatabaseTool` 就自动归到 db 那一组。
- 两个 Agent 用 `@Qualifier("databaseToolRegistry")` / `@Qualifier("aliasToolRegistry")` 拿对应的那把工具。
- **AI 永远不会看到不属于自己的工具**——既不会乱调，请求体也更小（少发不相关的 tool 描述）。

---

## 7. AbstractAgent 抽了什么？子类只剩什么？

`AbstractAgent.ask()` 里包了完整的 ReAct 循环。子类只需要回答 **4 件事**：

```java
@Service
public class DatabaseAgent extends AbstractAgent {

    public DatabaseAgent(AgentAiClient ai,
                         @Qualifier("databaseToolRegistry") ToolRegistry tr,  // ① 我用哪组工具
                         AgentProperties props,
                         AgentLogService log,
                         AgentSwitches switches) {
        super(ai, tr, props, log, switches);
    }

    @Override public String agentName() { return "database"; }                 // ② 我叫什么名字

    @Override protected String systemPrompt() {                                // ③ 对自己工作模式的说明（system prompt）
        return "你是一个数据库查询助手……";
    }

    @Override public String routerDescription() {                              // ④ 对 Router 的自我介绍（"什么场景该派给我"）
        return "用来查数据库表里的真实数据……";
    }
}
```

**就这四件事**。所有的循环、日志、异常处理、token 累加都在父类里。这就是为什么我们可以轻易地写出第二个 Agent。

> 注意 ③ 和 ④ 是**两种视角**：
> - `systemPrompt()` 是 agent 自己拿到任务后给大模型的指令（"你是个 xxx 助手，按这个步骤干"）；
> - `routerDescription()` 是给 Router 看的"自我推销"（"什么样的问题该派给我"）。Router 拿这个去拼路由 prompt，**只把当前开启的 agent 写进去**。

---

## 8. Router 是个什么"特殊 agent"？

Router **不是** AbstractAgent 的子类，因为它不需要循环、不需要工具，只需要做一次"分类"：

```
输入：用户消息
输出：{"target":"<某个 enabled agent 的 name>","confidence":0~1,"reason":"..."}
```

它复用 `AgentAiClient`，但调用时**传空的 tools 数组**。系统提示词里**强约束**只输出 JSON。

### 路由 prompt 是**动态拼**的（重点）

Router 没有把 "有哪些 agent 可选" 写死。每次收到请求时：

1. 通过 `List<AbstractAgent>` 拿到容器里所有 agent。
2. 用 `AgentSwitches` 过滤掉被关掉的，留下**当前开启的**。
3. 把每个开启 agent 的 `routerDescription()` 拼到 prompt 里。
4. 末尾约束 `target` 只能是这些名字之一。

**结果**：关掉一个 agent，AI **根本不会看到它存在**——也就不会派单过去。
不会再出现"AI 选了一个 agent，结果它已经被关了"这种尴尬。

### 几个边界情况

| 情况 | 行为 |
|------|------|
| 所有 agent 都关了 | 立刻抛错"没有任何 agent 处于开启状态"，**不调 LLM**（省 token）|
| 只剩 1 个 agent 开 | **短路掉 LLM**，直接派发；省 token、降延迟 |
| LLM 给的 target 是未知名字 | warn 一笔，fallback 到第一个开启的 agent |
| LLM 给的 target 已被关 | 同上 fallback（罕见——只在判定瞬间被人改了开关时才会发生）|
| LLM 调用失败 / JSON 解析失败 | 同上 fallback |

它的 LLM 调用同样落 `agent_ai_call_log`，`agent_name="router"`，方便你之后排查"为啥派错了"。

---

## 9. 三个 HTTP 入口

| 端点 | 做什么 | 用途 |
|------|-------|------|
| `POST /api/v1/agent/ask`      | 走路由，自动派发 | 真实业务入口 |
| `POST /api/v1/agent/database` | 跳过路由，直连 DatabaseAgent | 教学/调试 |
| `POST /api/v1/agent/alias`    | 跳过路由，直连 AliasAgent    | 教学/调试 |

请求体都一样：`{"message": "..."}`。

直连入口很有用——当你怀疑是路由判错了时，可以直接打到目标 agent 上验证。

---

## 9.5 Agent 开关（运行时关停某个 Agent）

### 这是干啥的？

有时候某个 agent 暂时有问题（比如下游 API 挂了、想做灰度、临时熔断），希望**立刻让它不再响应请求**，但又不想重启进程。这套开关就是给这个场景用的。

**特点**：
- 默认值写在 `application.yml`：`com.hollow.agent.enabled-agents.<name>`
- 运行时通过 admin 接口翻转，**变更只在内存**——重启进程会回到 yml 默认值（无持久化，简单透明）
- 关闭后，**路由派发 + 直连入口**都拒绝，语义统一
- 拒绝时返回 `{code:501, msg:"agent 已关闭: alias"}`

### 怎么配默认值

```yaml
com:
  hollow:
    agent:
      enabled-agents:
        database: true
        alias: true
```

没列出来的 agent 名按 **开启** 处理（避免新加 agent 时忘了配置导致全废）。

### Admin 接口

| 端点 | 做什么 |
|------|-------|
| `GET  /api/v1/agent/admin/switches` | 看当前所有 agent 的开关状态 |
| `POST /api/v1/agent/admin/switches` | 翻转，body: `{"agent":"alias","enabled":false}` |

示例：

```bash
# 关掉别名 agent
curl -X POST http://localhost:5777/api/v1/agent/admin/switches \
     -H 'Content-Type: application/json' \
     -d '{"agent":"alias","enabled":false}'

# 关了之后，无论走路由还是直连都会失败：
curl -X POST http://localhost:5777/api/v1/agent/alias \
     -H 'Content-Type: application/json' \
     -d '{"message":"大红莲"}'
# 返回：{"code":501,"msg":"agent 已关闭: alias"}
```

### 工作原理（一句话）

`AbstractAgent.ask()` 第一行就检查 `AgentSwitches.isEnabled(agentName())`，关了就抛 `AgentDisabledException`。无论谁调它（router 派发也好、controller 直连也好），都过不了这一关——**单一拦截点**。异常由 `GlobalExceptionHandler` 统一翻译成 501。

### 为什么不做持久化？

留作练习：如果想做，把 `AgentSwitches` 里的 `ConcurrentHashMap` 换成 Redis 存取即可（项目本来就接了 Redis）。教学场景下"重启回默认值"反而是一种安全网——开发时随便玩，部署上线就回到 yml 里定义好的状态。

---

## 10. 怎么加一个新 Agent？（重点：拓展指南）

假设你要加一个 **`OcrAgent`**（识别图片里的文字，已经存在 `OcrService` 之类）。完整步骤：

### Step 1：标记接口

`agent/ocr/OcrTool.java`：

```java
package com.hollow.build.agent.ocr;

import com.hollow.build.agent.core.Tool;

public interface OcrTool extends Tool {}
```

### Step 2：写 Tool 实现

`agent/ocr/tool/SubmitOcrTool.java`、`agent/ocr/tool/QueryOcrResultTool.java` 等：

```java
@Component
public class SubmitOcrTool implements OcrTool {
    @Override public String name()        { return "submit_ocr"; }
    @Override public String description() { return "提交一张图片做 OCR，返回 taskId……"; }
    @Override public Map<String,Object> parametersSchema() { /* JSON Schema */ }
    @Override public String execute(Map<String,Object> args) { /* 调真实业务 */ }
}
```

### Step 3：注册到 ToolRegistry

`agent/config/AgentToolRegistries.java` 多加一个 @Bean：

```java
@Bean
public ToolRegistry ocrToolRegistry(List<OcrTool> tools) {
    return new ToolRegistry(tools);
}
```

### Step 4：写 Agent

`agent/ocr/OcrAgent.java`：

```java
@Service
public class OcrAgent extends AbstractAgent {
    public OcrAgent(AgentAiClient ai,
                    @Qualifier("ocrToolRegistry") ToolRegistry tr,
                    AgentProperties props,
                    AgentLogService log,
                    AgentSwitches switches) {
        super(ai, tr, props, log, switches);
    }

    @Override public String agentName()    { return "ocr"; }

    @Override protected String systemPrompt() { return "你是 OCR 助手……"; }

    @Override public String routerDescription() {
        return """
                用来识别图片里的文字。
                典型场景：用户上传一张图说"帮我把这张截图里的字提出来"……
                """;
    }
}
```

### Step 5：~~让 Router 知道有这个新 Agent~~（不用做！）

🎉 **完全不用动 Router**：Spring 会自动把你的 `OcrAgent` 注入到 Router 的 `List<AbstractAgent>` 里；Router 调用时会从每个 agent 自己的 `routerDescription()` 现拼 prompt。**新增 agent 零侵入 Router**——这正是把描述放进 agent 自己的好处。

### Step 6（可选）：Controller 加直连入口

`agent/controller/AgentController.java` 加 `POST /api/v1/agent/ocr` 给调试用。

### Step 7（可选）：在 yml 里给开关默认值

```yaml
com.hollow.agent.enabled-agents:
  ocr: true
```

不加也行——`AgentSwitches` 没找到的名字按开启处理。

### Step 8：完工

不用改：`AbstractAgent`、`AgentRouter`、`ToolRegistry`、日志表、其他 Agent、其他 Tool。

> **整个新增过程零侵入**——这就是把每个 Agent 做成自包含模块、把"自我介绍"也写在 agent 自己身上的好处。

---

## 11. 怎么给已有 Agent 加一个新 Tool？

更简单，**只改一个文件夹**。例如要给 DatabaseAgent 加一个 `count_rows` 工具：

1. 在 `database/tool/` 新建 `CountRowsTool.java implements DatabaseTool`，加 `@Component`。
2. **完。**

Spring 启动时自动收集进 `databaseToolRegistry`，下次 ask 时 AI 就能看到并调用。系统提示词里**不一定要改**——如果工具描述写得清晰，AI 自己会判断什么时候用。

---

## 12. 常见问题 / 排错

**Q: AI 没调我新加的 Tool？**
A: 多半是 `description` 写得不够明确。模型选不选你这个工具，**只看 `name + description + parametersSchema`**。把 description 写成"什么场景下应该调我"。

**Q: `request_body` 太大，看不过来？**
A: 那是因为 `tools` 字段每次都把所有工具描述一起发——这是 OpenAI 协议要求，不是 bug。做日志统计时可以剥掉这一段单独存。

**Q: AI 一直循环不停？**
A: 由 `AgentProperties.maxIterations`（默认 6）兜底，达到上限会以 `MAX_ITERATIONS` 状态终止并落库。生产建议加报警。

**Q: 想关闭 Router，强制走某个 Agent？**
A: 直接调 `/api/v1/agent/database` 或 `/api/v1/agent/alias`，跳过路由层。

**Q: 加了新 Agent 后 Router 经常派错？**
A: 把你那个 agent 的 `routerDescription()` 写得更具体——典型场景、关键词都列上。Router 的 prompt 是从各 agent 的描述拼出来的，**写得越清楚派得越准**。改完不用重启 Router 类（在 agent 自己里）。

**Q: 关掉一个 agent 后，用户输入歪问题（"查查查查"），还是会被派给被关的 agent？**
A: 改造后**不会**了。Router 的 prompt 是动态拼的，被关的 agent 根本不会出现在候选清单里——AI 看不到，自然不会选。如果你还在看到，先确认是 `AgentSwitches.snapshot()` 真的把它标 false 了，再确认 Router 这次调用走的是新版代码（即 prompt 里只包含开启的 agent）。

---

## 13. 配置项一览（`application.yml` → `com.hollow.agent.*`）

```yaml
com:
  hollow:
    agent:
      max-iterations: 6        # Agent 主循环最多跑几轮
      max-query-limit: 50      # query_table 一次最多返多少行
      allowed-tables:          # AI 能查的表名白名单（数据库 agent 用）
        - blog_posts
        - event_news
        - role
      enabled-agents:          # 各 agent 启动时的默认开关（运行时可改、重启回默认值）
        database: true
        alias: true
```

模型 / API key / 代理走的是项目原本的 `com.hollow.ai.text-*`。

---

## 14. 推荐的学习顺序

1. 跑起来：`POST /api/v1/agent/ask {"message":"role 表里有几条数据？"}`
2. 看响应里的 `trace`——你能看到 AI 调了哪些工具、传了什么参数。
3. 去 `agent_ai_call_log` 按 `request_id` 排序，**逐行读 `request_body` 的 messages**——你会真切地看到对话怎么"长出来"的。
4. 改 `DatabaseAgent.systemPrompt()`，删掉"典型流程"那几句，再试一次——你会发现 AI 仍然能完成任务，但顺序可能不同了，由此理解 system prompt 的"引导"作用。
5. 调 `/api/v1/agent/alias`，看它调几次工具就能给出 `{"type":"武器","value":"赤风"}`。
6. 试着按第 10 节加一个新 Agent。
