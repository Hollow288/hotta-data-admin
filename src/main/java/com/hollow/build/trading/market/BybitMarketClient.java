package com.hollow.build.trading.market;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.trading.config.TradingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 普通 Java 查询服务，不依赖模型；所有请求固定为 category=spot 的公开 GET 接口。
 * 自有 HttpClient 不注册为公共 Bean，避免影响项目原有 AI HTTP 客户端的注入和代理配置。
 */
@Service
@Slf4j
public class BybitMarketClient {

    public static final List<String> SUPPORTED_INTERVALS = List.of(
            "1", "3", "5", "15", "30", "60", "120", "240", "360", "720", "D", "W", "M");
    public static final int MAX_KLINE_LIMIT = 200;

    private final TradingProperties properties;
    private final HttpClient httpClient;
    private final String baseUrl;

    @Autowired
    public BybitMarketClient(TradingProperties properties) {
        this(properties, createHttpClient(properties));
    }

    private static HttpClient createHttpClient(TradingProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.isProxyEnabled()) {
            if (properties.getProxyAddress() == null || properties.getProxyAddress().isBlank()
                    || properties.getProxyPort() < 1 || properties.getProxyPort() > 65535) {
                throw new IllegalArgumentException("Bybit 代理启用时必须配置有效的 proxy-address 和 proxy-port");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    properties.getProxyAddress().strip(), properties.getProxyPort())));
        }
        return builder.build();
    }

    BybitMarketClient(TradingProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
        if (properties.getRequestTimeout() == null || properties.getRequestTimeout().isNegative()
                || properties.getRequestTimeout().isZero()) {
            throw new IllegalArgumentException("trading.request-timeout 必须大于 0");
        }
        URI uri = URI.create(properties.getBybitBaseUrl());
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("trading.bybit-base-url 必须是无路径、参数和凭据的 HTTP(S) 服务地址");
        }
        this.baseUrl = properties.getBybitBaseUrl().replaceAll("/+$", "");
        log.info("Bybit 现货客户端初始化: baseUrl={}, network={}", baseUrl, networkDescription());
    }

    public SpotTicker ticker(String symbol) {
        String normalized = normalizeSymbol(symbol);
        Payload payload = request("/v5/market/tickers", Map.of("symbol", normalized));
        JSONObject item = firstItem(payload, normalized);
        return new SpotTicker(normalized,
                decimal(item, "lastPrice"), optionalDecimal(item, "bid1Price"),
                optionalDecimal(item, "ask1Price"),
                decimal(item, "price24hPcnt").multiply(BigDecimal.valueOf(100)),
                decimal(item, "volume24h"), decimal(item, "turnover24h"), payload.time());
    }

    public SpotInstrument instrument(String symbol) {
        String normalized = normalizeSymbol(symbol);
        Payload payload = request("/v5/market/instruments-info", Map.of("symbol", normalized));
        JSONObject item = firstItem(payload, normalized);
        JSONObject lot = requiredObject(item, "lotSizeFilter");
        JSONObject price = requiredObject(item, "priceFilter");
        return new SpotInstrument(normalized, text(item, "status"), text(item, "baseCoin"),
                text(item, "quoteCoin"), decimal(lot, "minOrderAmt"),
                decimal(lot, "basePrecision"), decimal(lot, "quotePrecision"),
                decimal(price, "tickSize"), optionalDecimal(lot, "maxLimitOrderQty"),
                optionalDecimal(lot, "maxMarketOrderQty"), payload.time());
    }

    public SpotKlines klines(String symbol, String interval, int limit) {
        return loadKlines(symbol, interval, limit, MAX_KLINE_LIMIT);
    }

    /** 指标内部取足够的预热数据；原始 K 线工具仍最多输出 200 根，避免挤占模型上下文。 */
    public SpotKlines indicatorKlines(String symbol, String interval) {
        return loadKlines(symbol, interval, 1000, 1000);
    }

    private SpotKlines loadKlines(String symbol, String interval, int limit, int maximum) {
        String normalized = normalizeSymbol(symbol);
        if (interval == null || !SUPPORTED_INTERVALS.contains(interval)) {
            throw new IllegalArgumentException("interval 必须是 " + SUPPORTED_INTERVALS);
        }
        if (limit < 1 || limit > maximum) {
            throw new IllegalArgumentException("limit 必须在 1 到 " + maximum + " 之间");
        }
        Payload payload = request("/v5/market/kline", Map.of(
                "symbol", normalized, "interval", interval, "limit", Integer.toString(limit)));
        if (!normalized.equals(payload.result().getString("symbol"))) {
            throw new BybitMarketException("Bybit K 线响应的交易对与请求不一致");
        }
        List<SpotKlines.Candle> candles = new ArrayList<>();
        for (int i = 0; i < payload.list().size(); i++) {
            if (!(payload.list().get(i) instanceof JSONArray row) || row.size() < 7) {
                throw new BybitMarketException("Bybit K 线数据不完整");
            }
            try {
                long start = Long.parseLong(row.getString(0));
                if (start <= 0) {
                    throw new IllegalArgumentException("开始时间必须大于 0");
                }
                candles.add(new SpotKlines.Candle(start,
                        new BigDecimal(row.getString(1)), new BigDecimal(row.getString(2)),
                        new BigDecimal(row.getString(3)), new BigDecimal(row.getString(4)),
                        new BigDecimal(row.getString(5)), new BigDecimal(row.getString(6)),
                        candleEnd(start, interval) <= payload.time()));
            } catch (RuntimeException e) {
                throw new BybitMarketException("Bybit K 线数值格式异常", e);
            }
        }
        candles.sort(Comparator.comparingLong(SpotKlines.Candle::startTimeEpochMillis));
        return new SpotKlines(normalized, interval, List.copyOf(candles), payload.time());
    }

    private Payload request(String path, Map<String, String> query) {
        String params = query.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + path + "?category=spot&" + params))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BybitMarketException("Bybit 行情查询被中断", e);
        } catch (IOException e) {
            String reason = e instanceof HttpConnectTimeoutException ? "连接超时"
                    : e instanceof HttpTimeoutException ? "请求超时" : "网络连接失败";
            throw new BybitMarketException("Bybit 行情接口连接失败或超时：" + reason
                    + "（" + e.getClass().getSimpleName() + "；" + networkDescription()
                    + "），请检查运行环境的网络连接", e);
        }
        if (response.statusCode() != 200) {
            throw new BybitMarketException("Bybit 行情接口返回 HTTP " + response.statusCode()
                    + "，请确认运行环境能够正常访问该服务");
        }
        try {
            JSONObject root = JSON.parseObject(response.body());
            if (root == null || root.getInteger("retCode") == null) {
                throw new BybitMarketException("Bybit 响应缺少 retCode");
            }
            if (root.getInteger("retCode") != 0) {
                throw new BybitMarketException("Bybit 查询失败，retCode=" + root.getInteger("retCode")
                        + ": " + root.getString("retMsg"));
            }
            Long time = root.getLong("time");
            if (time == null || time <= 0) {
                throw new BybitMarketException("Bybit 响应缺少有效数据时间");
            }
            JSONObject result = requiredObject(root, "result");
            if (!"spot".equals(result.getString("category"))) {
                throw new BybitMarketException("Bybit 响应不是现货数据");
            }
            JSONArray list = result.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                throw new BybitMarketException("Bybit 未返回该现货交易对的数据");
            }
            return new Payload(result, list, time);
        } catch (BybitMarketException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BybitMarketException("Bybit 返回的行情数据格式异常", e);
        }
    }

    private static JSONObject firstItem(Payload payload, String symbol) {
        if (!(payload.list().get(0) instanceof JSONObject item) || !symbol.equals(item.getString("symbol"))) {
            throw new BybitMarketException("Bybit 响应的交易对与请求不一致");
        }
        return item;
    }

    private static JSONObject requiredObject(JSONObject parent, String key) {
        JSONObject value = parent.getJSONObject(key);
        if (value == null) {
            throw new BybitMarketException("Bybit 响应缺少 " + key);
        }
        return value;
    }

    private static String text(JSONObject object, String key) {
        String value = object.getString(key);
        if (value == null || value.isBlank()) {
            throw new BybitMarketException("Bybit 响应缺少 " + key);
        }
        return value;
    }

    private static BigDecimal decimal(JSONObject object, String key) {
        try {
            return new BigDecimal(text(object, key));
        } catch (NumberFormatException e) {
            throw new BybitMarketException("Bybit 数值格式异常: " + key, e);
        }
    }

    private static BigDecimal optionalDecimal(JSONObject object, String key) {
        String value = object.getString(key);
        return value == null || value.isBlank() ? null : decimal(object, key);
    }

    private static String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{3,30}")) {
            throw new IllegalArgumentException("symbol 必须是交易对代码，例如 BTCUSDT，不能包含斜杠或其他符号");
        }
        return normalized;
    }

    private static long candleEnd(long start, String interval) {
        ZonedDateTime utc = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC);
        return switch (interval) {
            case "D" -> utc.plusDays(1).toInstant().toEpochMilli();
            case "W" -> utc.plusWeeks(1).toInstant().toEpochMilli();
            case "M" -> utc.plusMonths(1).toInstant().toEpochMilli();
            default -> utc.plusMinutes(Long.parseLong(interval)).toInstant().toEpochMilli();
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String networkDescription() {
        return properties.isProxyEnabled()
                ? "HTTP 代理 " + properties.getProxyAddress().strip() + ":" + properties.getProxyPort()
                : "未显式启用 Bybit 代理";
    }

    private record Payload(JSONObject result, JSONArray list, long time) {}
}
