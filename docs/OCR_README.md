# OCR 接口

`hotta-data-admin` 提供的 OCR 文字识别接口。采用 **提交-轮询** 模式：客户端先上传文件拿到 `taskId`，再凭 `taskId` 轮询识别结果。

底层走 RabbitMQ 异步处理，提交接口立即返回，不会阻塞 HTTP 连接等待识别完成。

---

## 端点一览

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/ocr/submit` | 上传图片或 PDF，返回 `taskId` |
| GET  | `/api/v1/ocr/result/{taskId}` | 凭 `taskId` 查询任务进度与结果 |

两个接口都标注了 `@PublicEndpoint`，无需登录即可调用。

---

## 统一响应体

所有响应都被包装为：

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

- `code = 200` 表示业务成功；其他值表示业务错误，错误信息见 `msg`
- `data` 部分见每个接口下的字段说明

> 注意：`code` 是**业务状态码**，HTTP 状态码始终为 200（除非框架层面异常）。前端要根据 `code` 字段判断成败，而不是 HTTP 状态码。

---

## POST /api/v1/ocr/submit — 提交任务

上传图片或 PDF，文件会被写入 MinIO 临时 bucket，并向 RabbitMQ `ocr.queue` 投递任务消息。接口立即返回 `taskId` 与 `PENDING` 状态。

### 请求

`Content-Type: multipart/form-data`

| 位置 | 名称 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- | --- |
| form  | `file` | file | 是 | — | 图片或 PDF 文件 |
| query | `mode` | `detail` \| `list` \| `text` | 否 | `detail` | 返回模式 |
| query | `minConfidence` | float `[0, 1]` | 否 | 不过滤 | 仅保留置信度 ≥ 该值的条目 |

`mode` 决定结果落到 `data` 的哪个字段：

- `detail`：含坐标 / 置信度 / 页码的结构化数组（落到 `results`）
- `list`：仅文本数组（落到 `textList`）
- `text`：所有识别文本拼接成一个字符串，**不插入空格或换行**（落到 `fullText`）

### 文件限制

| 项 | 限制 |
| --- | --- |
| 单文件大小 | 默认 50 MB（`com.hollow.ocr.max-file-size-bytes`） |
| 文件格式 | JPG / JPEG / PNG / WEBP / BMP / HEIC / HEIF / PDF |
| MIME 白名单 | `image/jpeg` `image/jpg` `image/png` `image/webp` `image/bmp` `image/heic` `image/heif` `application/pdf` |

未通过校验时直接返回业务错误，不会进入 MQ 队列。

### 响应 `data`

提交成功时返回任务初始记录：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...-...-...",
    "status": "PENDING",
    "retryCount": 0,
    "mode": "detail",
    "createdAt": 1746000000000,
    "updatedAt": 1746000000000
  }
}
```

成功后客户端只需要保留 `taskId`，其他字段都可在结果接口里再次拿到。

### 示例

```bash
# 默认 detail
curl -X POST "http://localhost:5777/api/v1/ocr/submit" \
  -F "file=@./sample.png"

# list 模式 + 置信度过滤
curl -X POST "http://localhost:5777/api/v1/ocr/submit?mode=list&minConfidence=0.8" \
  -F "file=@./sample.pdf"
```

### IP 限流

每个客户端 IP 每天最多调用本接口 **20 次**（由 `com.hollow.ocr.daily-ip-limit` 控制）。
超出后接口直接返回 `code = 429`，不会写入 MinIO 或入队 MQ：

```json
{
  "code": 429,
  "msg": "OCR 调用次数已达上限（每个 IP 每天 20 次），请明日再试",
  "data": null
}
```

说明：

- 限额仅作用于本接口（`/api/v1/ocr/submit`）；`/api/v1/ocr/result/{taskId}` 不受限，前端可正常轮询。
- 计数维度是"客户端 IP + 日期"。日期按服务器本地时区每日重置。
- 即使提交后被业务校验拦下（如文件类型错误），也会消耗一次额度，防止靠错误请求绕过限流。
- 真实 IP 通过 `X-Forwarded-For` 等代理头识别，请确保前置网关将这些头透传到本服务。

### 提交失败的常见原因

| `code` | `msg` | 触发条件 |
| --- | --- | --- |
| 400 | `请上传非空图片文件` | `file` 缺失或为空 |
| 400 | `OCR 图片不能超过 50.0MB` | 文件超过 `max-file-size-bytes` |
| 400 | `仅支持 JPG/PNG/WEBP/BMP/HEIC/HEIF 图片或 PDF 文件` | 文件类型不在白名单 |
| 400 | `mode 仅支持 detail / list / text` | `mode` 取了非法值 |
| 400 | `minConfidence 必须在 [0, 1] 之间` | `minConfidence` 越界 |
| 429 | `OCR 调用次数已达上限...` | 当前 IP 当天已用完 `daily-ip-limit` |
| 500 | `提交 OCR 任务失败: ...` | MinIO 写入失败 / RabbitMQ 未确认收到消息 |
| 502 | `OCR 服务地址未配置` | 后端未配置 `service-url`，属于运维问题 |

`500` 这种情况意味着 MQ 入队失败，任务**不会**留下假 PENDING——后端会立刻把它标记为 FAILED 后再返回错误。

---

## GET /api/v1/ocr/result/{taskId} — 查询结果

凭 `taskId` 查询任务当前状态。建议轮询间隔 **1~2 秒**。

### 路径参数

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | string | 提交任务时返回的 UUID |

### 状态机

```
PENDING ──> PROCESSING ──> SUCCESS
   │                  └──> FAILED
   └──────────────────────> FAILED  (排队超时或重试耗尽)
```

| status | 含义 | 客户端动作 |
| --- | --- | --- |
| `PENDING` | 已入 MQ 队列，尚未被消费者拉取 | 继续轮询 |
| `PROCESSING` | 消费者已开始识别 | 继续轮询 |
| `SUCCESS` | 识别完成 | 按 `mode` 读取 `results` / `textList` / `fullText`，停止轮询 |
| `FAILED` | 识别失败 | 读取 `errorMsg`，停止轮询 |

任务长时间卡在 `PENDING`（默认 300 秒，由 `pending-timeout-seconds` 控制）会被定时任务自动标记为 `FAILED`，避免前端无限轮询。

### 响应 `data` 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | string | 任务 ID |
| `status` | string | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED` |
| `retryCount` | int | 已重试次数；首次提交为 0 |
| `mode` | string | 本任务使用的返回模式 |
| `results` | array | `mode=detail` 成功时填充，见下方结构 |
| `textList` | string[] | `mode=list` 成功时填充 |
| `fullText` | string | `mode=text` 成功时填充；OCR 一字未识别时为 `""` |
| `pages` | int / null | PDF 总页数；图片时为 `null` |
| `elapseSeconds` | float | OCR 总耗时（秒，PDF 为各页累加） |
| `errorMsg` | string | 仅在 `FAILED` 时有值 |
| `createdAt` | long | 任务创建时间戳（毫秒） |
| `updatedAt` | long | 任务最后更新时间戳（毫秒） |

`results` 元素结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `text` | string | 识别文本 |
| `confidence` | float | 置信度，范围 `[0, 1]` |
| `bbox` | float[4][2] | 文本框 4 顶点坐标，顺序为左上、右上、右下、左下 |
| `page` | int / null | PDF 页码（1-based）；图片为 `null` |

### 示例响应

PENDING / PROCESSING：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...",
    "status": "PROCESSING",
    "retryCount": 0,
    "mode": "detail",
    "createdAt": 1746000000000,
    "updatedAt": 1746000001234
  }
}
```

SUCCESS（`mode=detail`）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...",
    "status": "SUCCESS",
    "retryCount": 0,
    "mode": "detail",
    "results": [
      {
        "text": "示例文字",
        "confidence": 0.987,
        "bbox": [[12.0, 34.0], [120.0, 34.0], [120.0, 60.0], [12.0, 60.0]],
        "page": 1
      }
    ],
    "pages": 1,
    "elapseSeconds": 0.42,
    "createdAt": 1746000000000,
    "updatedAt": 1746000003456
  }
}
```

SUCCESS（`mode=list`）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...",
    "status": "SUCCESS",
    "mode": "list",
    "textList": ["第一段文字", "第二段文字"],
    "pages": 1,
    "elapseSeconds": 0.42
  }
}
```

SUCCESS（`mode=text`）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...",
    "status": "SUCCESS",
    "mode": "text",
    "fullText": "第一段文字第二段文字",
    "pages": 1,
    "elapseSeconds": 0.42
  }
}
```

FAILED：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "taskId": "5b3e6c2c-...",
    "status": "FAILED",
    "retryCount": 3,
    "errorMsg": "OCR 处理失败: connection refused"
  }
}
```

任务不存在或已过期时（默认结果在 Redis 保留 1 小时）：

```json
{
  "code": 500,
  "msg": "任务不存在或已过期",
  "data": null
}
```

### 示例

```bash
curl "http://localhost:5777/api/v1/ocr/result/5b3e6c2c-...-...-..."
```

---

## 内部处理流程

调用方一般不需要关心，仅供排查问题时参考：

```
客户端
   │ POST /api/v1/ocr/submit
   ▼
OcrController.submit
   │
   ▼
OcrServiceImpl.submitTask
   ├─► MinIO  (写入临时 bucket: ocr-temp/ocr/{yyyy}/{MM}/{taskId}/{fileName})
   ├─► Redis  (key: ocr:result:{taskId}, status=PENDING)
   └─► RabbitMQ (exchange=ocr.exchange, routingKey=ocr.task)
                 │
                 ▼
            ocr.queue
                 │
                 ▼
         OcrConsumer.handleOcrTask
            ├─► Redis  (status=PROCESSING)
            ├─► MinIO  (读回文件)
            ├─► 调远程 OCR
            └─► Redis  (status=SUCCESS / FAILED, 写入识别结果)

客户端
   │ GET /api/v1/ocr/result/{taskId}
   ▼
OcrController.getResult ──► Redis (读 ocr:result:{taskId})
```

失败重试链路：

- 消费者识别失败时，先把消息投递到 `ocr.retry.queue`，借助 TTL 等 `retryDelayMillis`（默认 15 秒）后死信回主队列重试。
- 重试次数超过 `maxRetryCount`（默认 3）后，状态写入 `FAILED`，消息进入 `ocr.dead.queue` 供排查。

---

## 配置项

`application.yml` 下 `com.hollow.ocr.*`：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `service-url` | `http://127.0.0.1:7634/ocr` | 远程 OCR 服务地址（必须可达，否则提交会被前置校验拦下） |
| `api-key` | — | 远程 OCR 鉴权用，向远程发请求时通过 `X-API-KEY` 头携带；未配置时跳过 |
| `default-mode` | `detail` | `mode` 缺省时的回落值 |
| `default-min-confidence` | — | `minConfidence` 缺省时的回落值；不配置则不过滤 |
| `result-ttl` | `3600`（秒） | Redis 中任务记录的存活时间，超过后查询返回"任务不存在或已过期" |
| `max-file-size-bytes` | `52428800`（50 MB） | 上传文件体积上限 |
| `allowed-content-types` | 见 [文件限制](#文件限制) | 允许的 MIME 白名单 |
| `submit-confirm-timeout-millis` | `5000` | 等待 RabbitMQ Publisher Confirm 的超时（毫秒） |
| `max-retry-count` | `3` | 消费失败后最多重试次数 |
| `retry-delay-millis` | `15000` | 重试队列 TTL，即两次重试间隔（毫秒） |
| `pending-timeout-seconds` | `300` | 任务允许停留 PENDING 的最大秒数 |
| `pending-timeout-scan-interval-millis` | `60000` | 扫描超时 PENDING 任务的间隔（毫秒） |
| `minio-bucket` | `ocr-temp` | OCR 临时文件使用的 MinIO bucket |
| `daily-ip-limit` | `20` | 每个 IP 每天允许调用 `submit` 的最大次数（仅 `submit`，不限制查询） |

> Spring 的 multipart 上限（`spring.servlet.multipart.max-file-size` / `max-request-size`）也需要 ≥ `max-file-size-bytes`，否则文件会被框架层先拦截。

并发相关在 `spring.rabbitmq.listener.simple` 下：

```yaml
prefetch: 1
concurrency: 1
max-concurrency: 2
```

也就是默认最多 2 个消费者线程同时调用远程 OCR，可以按远程负载能力调整。

---

## 客户端集成建议

1. 提交后保留 `taskId`，**不要**根据返回的 `status` 立刻判定成败——`PENDING` 只是"已入队"。
2. 轮询间隔 1~2 秒；连续多次拿到 `SUCCESS` / `FAILED` 之外的状态时**不要无限轮询**，建议给前端一个最大等待时间（例如 5 分钟），与后端 `pending-timeout-seconds + maxRetryCount × retryDelayMillis` 对齐即可。
3. `errorMsg` 是给排查用的描述，不要把它当作机器可读的错误码做分支处理。
4. 大 PDF 识别耗时较长，提交后客户端可以提示"处理中"以改善体验。
