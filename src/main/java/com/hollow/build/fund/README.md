# 基金监控模块（com.hollow.build.fund）对接文档

> 本文档面向「基于 nonebot2 写 QQ 机器人插件」的开发者（含 AI）。
> 它是本服务对外的 **HTTP 接口契约**：插件通过这些接口添加/查询基金、拿到「点评数据包」，
> 再在**机器人侧**自行调用 AI 把数据翻译成点评文字。**本服务不直接产出点评文字，只产出数据。**

---

## 1. 整体数据流

```
QQ 用户 ──指令──► nonebot2 插件 ──HTTP──► 本服务(Spring Boot) ──► MySQL / 东方财富公开接口
                       │                        ▲
                       │   GET /review 拿数据包  │
                       ▼                        │
                  组装 prompt ──► 调用 AI ──► 点评文字 ──► 回复 QQ 用户
```

- **读接口**（列表/走势/最新/点评）：公开，免登录、免密钥。
- **写接口**（添加/取消/同步）：公开路由，但需在请求头带密钥 `X-FUND-TOKEN`。
- 净值/画像/排名由服务端**定时任务**每天自动抓取入库（每晚 23:30 + 次日 05:30 兜底），
  插件正常**只读**即可，无需主动触发同步。

---

## 2. 通用约定

### 2.1 Base URL

```
http://<服务器IP>:5777
```

- 默认端口 `5777`，无额外 context-path。
- 本模块所有接口前缀：`/api/v1/fund`

### 2.2 统一响应包装

所有接口都返回如下结构（`ApiResponse`）：

```jsonc
{
  "code": 200,        // 业务状态码，200=成功
  "msg": "成功",       // 提示信息
  "data": { ... }     // 业务数据，可能是对象/数组/null
}
```

**判断成功：`code == 200`**。常见非 200：

| code | 含义 | 触发场景 |
|------|------|----------|
| 400 | 请求参数不正确 | 添加时代码为空 / 代码查不到对应基金 |
| 401 | 鉴权未通过 | 写接口 `X-FUND-TOKEN` 缺失或不匹配 |
| 404 | 请求未找到 | 查询一只**未在监控列表**的基金 |
| 500 | 系统异常 | 服务端内部错误 |

> 插件务必先判 `code`，再读 `data`；非 200 时把 `msg` 直接回给用户即可。

### 2.3 认证（仅写接口）

- 写接口（`POST /`、`/sync`、`/{code}/sync-snapshot`、`/{code}/disable`）需请求头：
  ```
  X-FUND-TOKEN: <密钥>
  ```
- 密钥值见服务端配置 `com.hollow.fund.api-token`（环境变量 `FUND_API_TOKEN`）。
  **请向服务部署方索取，不要硬编码到公开仓库。** 若服务端该配置留空，则写接口不校验（自用/内网）。

### 2.4 字段序列化约定

- 金额/比率类（`BigDecimal`）→ JSON **数字**，可能为 `null`。
- 日期 `LocalDate` → 字符串 `"yyyy-MM-dd"`（如 `"2026-06-06"`）。
- 时间 `LocalDateTime` → ISO-8601 字符串（具体格式以实际返回为准）。
- 任何业务字段都**可能为 null**（数据尚未抓到时），插件需做空值兜底。

---

## 3. 接口清单

| # | 方法 | 路径 | 用途 | 认证 |
|---|------|------|------|------|
| 1 | GET  | `/api/v1/fund/list` | 监控中的基金列表 | 否 |
| 2 | GET  | `/api/v1/fund/{code}/trend?days=90` | 近 N 天净值涨跌走势（画图用） | 否 |
| 3 | GET  | `/api/v1/fund/{code}/latest` | 最新净值与当日涨跌 | 否 |
| 4 | GET  | `/api/v1/fund/{code}/review` | **点评数据包**（喂给 AI） | 否 |
| 5 | POST | `/api/v1/fund` | 添加监控（回补历史、画像、排名） | 是 |
| 6 | POST | `/api/v1/fund/{code}/disable` | 取消监控（软停用，保留历史） | 是 |
| 7 | POST | `/api/v1/fund/sync` | 手动全量同步所有监控基金 | 是 |
| 8 | POST | `/api/v1/fund/{code}/sync-snapshot` | 手动刷新单只阶段排名快照 | 是 |

> `{code}` = 6 位基金代码，如 `270042`。

---

### 接口 1 · 监控基金列表

```
GET /api/v1/fund/list
```

`data` 为 `Fund[]`（见 §4.1）。

```bash
curl "http://<host>:5777/api/v1/fund/list"
```

```jsonc
{
  "code": 200, "msg": "成功",
  "data": [
    { "fundCode": "270042", "fundName": "广发纳斯达克100ETF联接(QDII)A",
      "fundType": "QDII", "enabled": 1, "latestNavDate": "2026-06-06" }
  ]
}
```

---

### 接口 2 · 涨跌走势

```
GET /api/v1/fund/{code}/trend?days=90
```

- `days`（query，可选，默认 `90`）：往前取多少天的净值。
- 基金未监控 → `code=404`，`msg="该基金未在监控列表中，请先添加"`。

`data` 为 `FundTrendDto`（见 §4.2），`points` 按日期**升序**，适合直接画折线。

```bash
curl "http://<host>:5777/api/v1/fund/270042/trend?days=30"
```

```jsonc
{
  "code": 200, "msg": "成功",
  "data": {
    "fundCode": "270042", "fundName": "广发纳斯达克100ETF联接(QDII)A", "fundType": "QDII",
    "days": 30,
    "latestNavDate": "2026-06-06", "latestNav": 5.1234, "latestGrowthRate": 0.85,
    "periodGrowthRate": 4.20,
    "points": [
      { "date": "2026-05-08", "unitNav": 4.91, "growthRate": -0.30 },
      { "date": "2026-06-06", "unitNav": 5.1234, "growthRate": 0.85 }
    ],
    "note": null
  }
}
```

---

### 接口 3 · 最新净值

```
GET /api/v1/fund/{code}/latest
```

`data` 同样是 `FundTrendDto`，但 `days=0`、`points` 只含最新一条。基金未监控 → `code=404`。

```jsonc
{
  "code": 200, "msg": "成功",
  "data": {
    "fundCode": "270042", "fundName": "广发纳斯达克100ETF联接(QDII)A", "fundType": "QDII",
    "days": 0,
    "latestNavDate": "2026-06-06", "latestNav": 5.1234, "latestGrowthRate": 0.85,
    "periodGrowthRate": null,
    "points": [ { "date": "2026-06-06", "unitNav": 5.1234, "growthRate": 0.85 } ],
    "note": null
  }
}
```

---

### 接口 4 · 点评数据包 ⭐（AI 点评的输入）

```
GET /api/v1/fund/{code}/review
```

聚合「阶段涨跌 + 风险指标 + 排名/规模/经理 + 近期走势」，一次性打包。
**指标都由服务端算好**，AI 端只需把数字翻译成人话，不必自己计算。基金未监控 → `code=404`。

`data` 为 `FundReviewDto`（见 §4.3）。完整示例：

```jsonc
{
  "code": 200, "msg": "成功",
  "data": {
    "fundCode": "270042", "fundName": "广发纳斯达克100ETF联接(QDII)A", "fundType": "QDII",
    "asOf": "2026-06-06", "latestNav": 5.1234, "latestGrowthRate": 0.85,

    "return1m": 3.21, "return3m": 8.50, "return6m": 12.30, "return1y": 25.60,

    "maxDrawdown": -15.20, "volatility": 18.40, "annualizedReturn": 22.10,
    "sharpeRatio": 1.20, "calmarRatio": 1.45,
    "highNav": 5.30, "highDate": "2026-05-20", "lowNav": 4.10, "lowDate": "2025-09-15",

    "similarPercent": 88.50, "fundScale": 52.34,
    "managerName": "张三", "managerStar": 4, "managerWorkTime": "8年又120天",
    "managerSize": "120.50亿", "managerScore": 85.20, "buyRate": 0.12,

    "periodRanks": [
      { "periodCode": "Y", "periodName": "近1月", "fundReturn": 3.21,
        "similarAverageReturn": 2.10, "benchmarkReturn": 1.80,
        "rankNo": 120, "rankTotal": 850, "asOf": "2026-06-06" }
    ],

    "recent": [ { "date": "2026-06-06", "unitNav": 5.1234, "growthRate": 0.85 } ],

    "note": null
  }
}
```

> **注意**：本数据包**不含持仓明细**（持仓接口长期无数据，已下线）。
> `note` 仅在「货币基金 / 画像或排名暂缺」时非空，非空时建议把它附在点评末尾提示用户。

---

### 接口 5 · 添加监控

```
POST /api/v1/fund
Header: X-FUND-TOKEN: <密钥>
Body  : {"code": "270042"}
```

服务端会校验代码有效性、入库，并**同步回补**历史净值/画像/阶段排名（耗时可能数秒）。

- 成功：`data` 为入库后的 `Fund`。
- `code` 为空：`code=400`，`msg="基金代码不能为空"`。
- 代码查不到：`code=400`，`msg="未找到该基金代码，请检查后重试"`。
- 密钥错误：`code=401`。

```bash
curl -X POST "http://<host>:5777/api/v1/fund" \
  -H "Content-Type: application/json" \
  -H "X-FUND-TOKEN: <密钥>" \
  -d '{"code":"270042"}'
```

---

### 接口 6 · 取消监控

```
POST /api/v1/fund/{code}/disable
Header: X-FUND-TOKEN: <密钥>
```

软停用（`enabled=0`），**保留历史净值**。`data` 为 `null`，`code=200` 即成功。
（即使该基金不存在也返回成功，是幂等操作。）

---

### 接口 7 · 手动全量同步

```
POST /api/v1/fund/sync
Header: X-FUND-TOKEN: <密钥>
```

立即对所有监控基金跑一次「净值增量 + 画像 + 阶段排名」刷新，`data` 为 `null`。
一般给运维用；日常有定时任务，插件**通常用不到**。

---

### 接口 8 · 手动刷新单只快照

```
POST /api/v1/fund/{code}/sync-snapshot
Header: X-FUND-TOKEN: <密钥>
```

只刷新该基金的阶段排名（不拉净值）。基金未监控 → `code=404`。`data` 见 §4.5。

```jsonc
{ "code": 200, "msg": "成功",
  "data": { "fundCode": "270042", "periodRankCount": 8, "note": null } }
```

---

## 4. 数据结构

### 4.1 Fund（基金实体）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 主键 |
| `fundCode` | string | 基金代码（6位） |
| `fundName` | string | 基金名称 |
| `fundType` | string | 类型（QDII/指数型-股票/债券型/货币型…） |
| `enabled` | number | 是否监控：1 是 / 0 否 |
| `latestNavDate` | string(date) | 已入库的最新净值日期 |
| `createTime` | string(datetime) | 创建时间 |
| `updateTime` | string(datetime) | 更新时间 |

### 4.2 FundTrendDto（走势）

| 字段 | 类型 | 说明 |
|------|------|------|
| `fundCode` / `fundName` / `fundType` | string | 基金基本信息 |
| `days` | number | 查询天数区间（`latest` 接口为 0） |
| `latestNavDate` | string(date) | 数据截至日期 |
| `latestNav` | number | 最新单位净值 |
| `latestGrowthRate` | number | 最新一日涨跌幅(%) |
| `periodGrowthRate` | number | 区间涨跌幅(%)，`latest` 接口为 null |
| `points` | FundNavPoint[] | 净值序列，**按日期升序** |
| `note` | string | 额外提示（如货币基金无涨跌），可能 null |

### 4.2.1 FundNavPoint（走势数据点）

| 字段 | 类型 | 说明 |
|------|------|------|
| `date` | string(date) | 净值日期 |
| `unitNav` | number | 单位净值 |
| `growthRate` | number | 当日涨跌幅(%) |

### 4.3 FundReviewDto（点评数据包）

| 分组 | 字段 | 类型 | 说明 / 来源 |
|------|------|------|-------------|
| 基本 | `fundCode`/`fundName`/`fundType` | string | 基金信息 |
| 基本 | `asOf` | string(date) | 净值截至日期 |
| 基本 | `latestNav` | number | 最新单位净值 |
| 基本 | `latestGrowthRate` | number | 最新一日涨跌幅(%) |
| 阶段涨跌 | `return1m`/`return3m`/`return6m`/`return1y` | number | 近1月/3月/6月/1年涨跌幅(%)，来自画像 |
| 风险 | `maxDrawdown` | number | 近1年最大回撤(%)，**负值**，服务端算 |
| 风险 | `volatility` | number | 近1年年化波动率(%) |
| 风险 | `annualizedReturn` | number | 近1年年化收益率(%) |
| 风险 | `sharpeRatio` | number | 夏普比率 |
| 风险 | `calmarRatio` | number | 卡玛比率 |
| 风险 | `highNav`/`highDate` | number/date | 区间最高净值及日期 |
| 风险 | `lowNav`/`lowDate` | number/date | 区间最低净值及日期 |
| 相对/背景 | `similarPercent` | number | 同类排名百分位(0-100，越大越靠前) |
| 相对/背景 | `fundScale` | number | 最新规模(亿元) |
| 相对/背景 | `managerName` | string | 基金经理 |
| 相对/背景 | `managerStar` | number | 经理星级 |
| 相对/背景 | `managerWorkTime` | string | 任职年限（原样文本，如「8年又120天」） |
| 相对/背景 | `managerSize` | string | 在管规模（原样文本） |
| 相对/背景 | `managerScore` | number | 经理综合评分 |
| 相对/背景 | `buyRate` | number | 申购费率(%) |
| 阶段排名 | `periodRanks` | FundPeriodRank[] | 阶段收益/同类平均/基准/排名，见 §4.4 |
| 走势 | `recent` | FundNavPoint[] | 最近约10个交易日走势 |
| 提示 | `note` | string | 货币基金/数据暂缺提示，可能 null |

> ⚠ 画像组（return*、similarPercent、fundScale、manager*、buyRate）会在画像尚未抓到时**整组为 null**；
> 风险组在净值不足时为 null。插件 / AI 都要容忍缺字段。

### 4.4 FundPeriodRank（阶段排名）

| 字段 | 类型 | 说明 |
|------|------|------|
| `periodCode` | string | 阶段编码：`Z`近1周 `Y`近1月 `3Y`近3月 `6Y`近6月 `1N`近1年 `2N`近2年 `3N`近3年 `5N`近5年 `JN`今年以来 `LN`成立以来 |
| `periodName` | string | 阶段中文名 |
| `fundReturn` | number | 基金阶段收益率(%) |
| `similarAverageReturn` | number | 同类平均收益率(%) |
| `benchmarkReturn` | number | 基准收益率(%)，当前为沪深300 |
| `rankNo` | number | 同类排名 |
| `rankTotal` | number | 同类总数 |
| `asOf` | string(date) | 数据截至日期 |

### 4.5 FundSnapshotSyncResultDto（同步结果）

| 字段 | 类型 | 说明 |
|------|------|------|
| `fundCode` | string | 基金代码 |
| `periodRankCount` | number | 本次写入的阶段排名条数 |
| `note` | string | 备注，可能 null |

---

## 5. nonebot2 插件建议

### 5.1 指令映射建议

| 用户指令 | 调用接口 |
|----------|----------|
| `基金列表` | GET `/list` |
| `基金 270042` / `基金最新 270042` | GET `/{code}/latest` |
| `基金走势 270042 30` | GET `/{code}/trend?days=30` |
| `基金点评 270042` | GET `/{code}/review` → 组 prompt → AI |
| `添加基金 270042` | POST `/`（带 token） |
| `删除基金 270042` | POST `/{code}/disable`（带 token） |

> 建议把「添加/删除」限制为管理员（nonebot2 的 `SUPERUSER` 权限），普通群友只给查询。

### 5.2 调用示例（httpx）

```python
import httpx

BASE = "http://<host>:5777/api/v1/fund"
FUND_TOKEN = "<从服务端 com.hollow.fund.api-token 获取>"

async def get_review(code: str) -> dict | None:
    async with httpx.AsyncClient(timeout=20) as client:
        r = await client.get(f"{BASE}/{code}/review")
        body = r.json()
        if body.get("code") != 200:
            return None          # 把 body["msg"] 回给用户
        return body["data"]

async def add_fund(code: str) -> dict:
    async with httpx.AsyncClient(timeout=30) as client:   # 添加会回补历史，超时给大一点
        r = await client.post(BASE, json={"code": code},
                              headers={"X-FUND-TOKEN": FUND_TOKEN})
        return r.json()
```

### 5.3 把 review 数据交给 AI 点评

`review` 已是结构化指标，**直接把整个 `data` JSON 塞进 prompt** 即可，让 AI 翻译成人话，并强约束「只依据给定数据、不要编造、不构成投资建议」。示例：

```python
SYSTEM = (
    "你是基金点评助手。只依据用户给出的 JSON 指标点评，不得编造数据。"
    "用通俗中文，先一句话结论，再分『收益表现/风险/同类排名/经理』点评，"
    "最后提示『仅供参考，不构成投资建议』。缺失(null)的指标不要提。"
)

async def review_text(code: str) -> str:
    data = await get_review(code)
    if not data:
        return "该基金未在监控列表，请先 添加基金 270042"
    import json
    user_msg = f"请点评这只基金，数据如下：\n{json.dumps(data, ensure_ascii=False)}"
    # → 调你机器人侧的 AI（OpenAI 兼容 / DeepSeek 等），返回文本
    if data.get("note"):
        ...  # 可把 note 追加到结尾提示
```

> 若希望「点评文字由服务端直接返回、插件只发代码」，本仓库另有通用 AI 调用体系
> `com.hollow.build.ai`（OpenAI Chat Completions 兼容，含多 key 轮询/限流降级），
> 可在服务端加一个 `reviewByAi(code)` 接口复用——那样插件就不必自己接 AI。

---

## 6. 易错点

1. **先判 `code==200` 再读 `data`**；非 200 时 `data` 多为 null，`msg` 是给用户看的原因。
2. **查询未监控的基金返回 404**，不是空数据——提示用户先「添加基金」。
3. **写接口必须带 `X-FUND-TOKEN`**，否则 401；token 从服务端配置取，勿写进公开仓库。
4. **几乎所有业务字段都可能为 null**（数据未抓到），渲染前做空值兜底。
5. **添加基金较慢**（同步回补历史净值），httpx 超时给到 30s 左右，并给用户「正在添加…」反馈。
6. `maxDrawdown` 是**负数**；`points`/`recent` 已**按日期升序**，画图无需再排序。
7. 数据**不含持仓明细**，prompt 里不要要求 AI 分析持仓。
