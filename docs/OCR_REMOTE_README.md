# OCR Service

基于 [RapidOCR (ONNX Runtime)](https://github.com/RapidAI/RapidOCR) + FastAPI 的轻量 OCR 服务，支持图片与 PDF 识别，支持本地文件上传与远程 URL 拉取。

## 特性

- 图片 OCR：JPG / PNG / BMP / WebP / **HEIC / HEIF** 等 Pillow 支持的格式
- PDF OCR：基于 PyMuPDF 渲染逐页识别，可配置 DPI 与最大页数
- 三种入口：`multipart` 文件上传、远程 URL 拉取、**base64 JSON**
- 远程 URL 拉取：内置 SSRF 防护，禁止访问私有/回环/保留地址（可关闭）
- API Key 鉴权（请求头 `X-API-KEY`）
- 三种返回模式：`detail`（含坐标/置信度）、`list`（纯文本数组）、`text`（合并为一段完整文本）
- 置信度阈值过滤
- 上传体积上限（`UPLOAD_MAX_BYTES`），超限返 413
- 结构化 JSON access log，每条响应自动带 `X-Request-ID`
- OCR 计算 off 主事件循环（`run_in_threadpool`），并发不阻塞

## 快速开始

### Docker Compose

```bash
cp .env.example .env
# 编辑 .env，至少修改 OCR_API_KEY
docker compose up -d
```

服务默认监听 `127.0.0.1:7634`（容器内 `8000`）。

### 本地运行

```bash
poetry install
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OCR_API_KEY` | `my_default_secret` | API Key，请求头 `X-API-KEY` 必须与之匹配 |
| `PDF_DPI` | `200` | PDF 页面渲染 DPI，越高越清晰也越慢 |
| `PDF_MAX_PAGES` | `50` | PDF 最大页数，超出返回 413 |
| `URL_DOWNLOAD_TIMEOUT` | `15` | URL 下载超时（秒） |
| `URL_MAX_BYTES` | `52428800` | URL 下载最大字节数（默认 50 MB） |
| `URL_MAX_REDIRECTS` | `3` | URL 最大重定向次数 |
| `ALLOW_PRIVATE_URLS` | `false` | 是否允许下载到私有/回环 IP（生产环境保持 `false`） |
| `UPLOAD_MAX_BYTES` | `52428800` | 本地上传 / base64 解码后的最大字节数（默认 50 MB） |
| `LOG_LEVEL` | `INFO` | 日志级别（`DEBUG` / `INFO` / `WARNING` / `ERROR`） |

## 鉴权

所有 OCR 接口均需在请求头携带 API Key：

```
X-API-KEY: <your-api-key>
```

未携带或不匹配返回 `403`。

## 接口

### 健康检查

```http
GET /
```

返回：

```json
{ "message": "OCR 服务正在运行中...请使用 POST /ocr 上传图片或 PDF" }
```

---

### `POST /ocr` — 文件上传

`multipart/form-data` 上传本地图片或 PDF。

#### 请求

| 位置 | 名称 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- | --- |
| header | `X-API-KEY` | string | 是 | — | API Key |
| form | `file` | file | 是 | — | 图片或 PDF 文件 |
| query | `mode` | `detail` \| `list` \| `text` | 否 | `detail` | 返回格式 |
| query | `min_confidence` | float `[0, 1]` | 否 | 不过滤 | 仅返回置信度 ≥ 该值的条目 |

#### 示例：默认 detail 模式

```bash
curl -X POST "http://localhost:7634/ocr" \
  -H "X-API-KEY: your-api-key" \
  -F "file=@./sample.png"
```

#### 示例：list 模式 + 置信度过滤

```bash
curl -X POST "http://localhost:7634/ocr?mode=list&min_confidence=0.8" \
  -H "X-API-KEY: your-api-key" \
  -F "file=@./sample.pdf"
```

---

### `POST /ocr/url` — 远程 URL

通过 JSON body 提交远程 URL，由服务端拉取后识别。

#### 请求

Header：`X-API-KEY: <your-api-key>`、`Content-Type: application/json`

Body：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `url` | string (http/https) | 是 | — | 资源地址 |
| `mode` | `detail` \| `list` \| `text` | 否 | `detail` | 返回格式 |
| `min_confidence` | float `[0, 1]` | 否 | 不过滤 | 仅返回置信度 ≥ 该值的条目 |

#### 示例：默认 detail 模式

```bash
curl -X POST "http://localhost:7634/ocr/url" \
  -H "X-API-KEY: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/sample.png"
  }'
```

#### 示例：list 模式 + 置信度过滤

```bash
curl -X POST "http://localhost:7634/ocr/url" \
  -H "X-API-KEY: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/sample.pdf",
    "mode": "list",
    "min_confidence": 0.8
  }'
```

---

### `POST /ocr/base64` — base64 JSON

通过 JSON body 直接提交 base64 编码的图片或 PDF，适合前端/Java 等不便走 multipart 的场景。

#### 请求

Header：`X-API-KEY: <your-api-key>`、`Content-Type: application/json`

Body：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `data` | string | 是 | — | base64 编码字节；兼容 `data:image/png;base64,...` 形式的 data URL |
| `mime` | string | 否 | 自动 | MIME 提示（如 `application/pdf`）；缺省则按 `%PDF-` 魔术字节自动判断是否为 PDF |
| `mode` | `detail` \| `list` \| `text` | 否 | `detail` | 返回格式 |
| `min_confidence` | float `[0, 1]` | 否 | 不过滤 | 置信度阈值 |

解码后超过 `UPLOAD_MAX_BYTES` 直接返 413；`data` 不是合法 base64 返 400。

#### 示例

```bash
B64=$(base64 -w0 ./sample.png)
curl -X POST "http://localhost:7634/ocr/base64" \
  -H "X-API-KEY: your-api-key" \
  -H "Content-Type: application/json" \
  -d "{\"data\": \"$B64\", \"mode\": \"text\"}"
```

---

## 响应头

每个响应都会带上：

- `X-Request-ID`：12 位十六进制字符串，用于跟踪日志。如果客户端在请求里带了 `X-Request-ID`，服务会原样回显。

## 日志

启动后所有日志都是单行 JSON（stdout），便于直接喂给 Loki / ELK：

```json
{"ts":"2026-04-25T10:00:00","level":"INFO","logger":"ocr.access","msg":"access","request_id":"6f8695025172","method":"POST","path":"/ocr","status":200,"duration_ms":421.3,"client":"127.0.0.1","content_length":"58234"}
```

---

## 返回格式

### `mode=detail`（默认）

```json
{
  "code": 200,
  "data": [
    {
      "text": "示例文字",
      "confidence": 0.987,
      "bbox": [[12.0, 34.0], [120.0, 34.0], [120.0, 60.0], [12.0, 60.0]],
      "page": 1
    }
  ],
  "elapse": 0.42,
  "pages": 1
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，固定 `200` |
| `data[].text` | string | 识别文本 |
| `data[].confidence` | float | 置信度，范围 `[0, 1]` |
| `data[].bbox` | float[4][2] | 文本框 4 个顶点坐标，顺序为左上、右上、右下、左下 |
| `data[].page` | int / null | 页码（PDF 时为 1-based；图片时为 `null`） |
| `elapse` | float | OCR 总耗时（秒，PDF 为各页累加） |
| `pages` | int / null | PDF 总页数；图片为 `null` |

### `mode=list`

```json
{
  "code": 200,
  "data": ["第一段文字", "第二段文字", "第三段文字"],
  "elapse": 0.42,
  "pages": 1
}
```

`data` 仅保留文本，按识别顺序（PDF 按页码顺序）排列。

### `mode=text`

```json
{
  "code": 200,
  "data": "第一段文字第二段文字第三段文字",
  "elapse": 0.42,
  "pages": 1
}
```

`data` 为一个字符串：将所有识别文本按识别顺序（PDF 按页码顺序）直接拼接，**不插入空格或换行**。
适合需要"一段完整文本"的下游处理（如送大模型摘要、关键词提取等）。

> ⚠️ 注意：OCR 是按"文本检测框"切分的，相邻框之间没有可靠的"换行"信号——同一行的文字可能被切成多个框，跨行的文字也可能被合到一框。因此本模式刻意**不输出换行**，避免误导。如需保留版面层级，请用 `mode=list` 自己再处理，或使用 `mode=detail` 拿到 `bbox` 后按需聚合。

---

## 错误响应

| HTTP | 场景 | 示例 detail |
| --- | --- | --- |
| 400 | 不支持的图片格式 | `Unsupported file format` |
| 400 | PDF 解析失败 | `Invalid PDF: <reason>` |
| 400 | URL 下载失败 / SSRF 拦截 / 重定向超限 | `Failed to download: ...` / `URL resolves to restricted IP: ...` / `Too many redirects` |
| 400 | base64 非法或为空 | `Invalid base64 payload` / `Empty payload` |
| 403 | API Key 缺失或错误 | `Could not validate credentials` |
| 413 | PDF 页数超限 | `PDF has 80 pages, exceeds limit of 50` |
| 413 | 上传字节数超 `UPLOAD_MAX_BYTES` | `Upload exceeds max size of ... bytes` |
| 422 | 参数校验失败（如 `min_confidence` 超出 `[0, 1]`） | FastAPI 标准校验响应 |

---

## 测试

```bash
poetry install --with dev
poetry run pytest tests/
```

测试在 `tests/conftest.py` 里 stub 掉了 `rapidocr_onnxruntime`，所以无需加载真实 ONNX 模型即可跑全套校验/安全边界测试（鉴权、SSRF、上传大小、PDF 页数、URL 重定向、base64 解码等）。

## OpenAPI 文档

服务启动后访问：

- Swagger UI: `http://localhost:7634/docs`
- ReDoc: `http://localhost:7634/redoc`
