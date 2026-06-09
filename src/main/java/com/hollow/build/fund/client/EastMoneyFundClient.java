package com.hollow.build.fund.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.fund.FundNav;
import com.hollow.build.fund.FundPeriodRank;
import com.hollow.build.fund.FundProfile;
import com.hollow.build.fund.config.FundConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富/天天基金公开接口客户端。
 * <p>
 * 负责三件事：
 * <ol>
 *   <li>{@link #searchFund(String)} —— 校验基金代码是否存在并取名称/类型（搜索接口）；</li>
 *   <li>{@link #fetchNavHistory(String, int, int)} —— 拉取官方历史真实净值（lsjz 接口，需带 Referer）；</li>
 *   <li>{@link #fetchProfile(String)} —— 拉取基金画像（pingzhongdata，JS 文件，正则提取阶段涨幅/排名/规模/经理）。</li>
 * </ol>
 * 均为非官方接口，调用方需自行处理失败与限流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyFundClient {

    /** 国内接口，不走代理，独立短连接超时（参照 OcrRemoteClient 的 static HttpClient 写法）。 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 常规浏览器 UA，部分接口对空 UA 会拒绝。 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /** 移动端 JSON 接口对完整浏览器 UA 偶发返回 61136403，实测简短 UA 更稳定。 */
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0";

    /** 基金搜索/联想接口（用于校验代码 + 取名称、类型）。 */
    private static final String SEARCH_URL = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=";

    /** 历史净值接口（官方真实净值，必须带 Referer）。 */
    private static final String LSJZ_URL = "https://api.fund.eastmoney.com/f10/lsjz";

    /** 基金画像数据接口（JS 文件，含阶段涨幅/排名/规模/经理等）。 */
    private static final String PINGZHONG_URL = "https://fund.eastmoney.com/pingzhongdata/";

    /** 天天基金移动端公开接口（JSON，补充阶段排名/主要持仓）。 */
    private static final String MOBILE_API_URL = "https://fundmobapi.eastmoney.com/FundMNewApi/";

    private final FundConfigurationProperties fundConfig;

    /**
     * 按基金代码精确查询基金基本信息，用于添加监控时校验代码并自动取名称/类型。
     *
     * @param code 基金代码
     * @return 匹配到的基金基本信息；代码不存在或调用失败返回 {@code null}
     */
    public FundBasic searchFund(String code) {
        try {
            URI uri = URI.create(SEARCH_URL + URLEncoder.encode(code, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(fundConfig.getRequestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("基金搜索接口异常状态码: {}, code={}", resp.statusCode(), code);
                return null;
            }
            JSONObject root = JSON.parseObject(resp.body());
            JSONArray datas = root == null ? null : root.getJSONArray("Datas");
            if (datas == null || datas.isEmpty()) {
                return null;
            }
            // 搜索可能返回多个候选，取代码精确匹配的那一条
            for (int i = 0; i < datas.size(); i++) {
                JSONObject item = datas.getJSONObject(i);
                if (item != null && code.equals(item.getString("CODE"))) {
                    String name = item.getString("NAME");
                    String type = null;
                    JSONObject base = item.getJSONObject("FundBaseInfo");
                    if (base != null) {
                        type = base.getString("FTYPE");
                    }
                    return new FundBasic(code, name, type);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("调用基金搜索接口失败 code={}", code, e);
            return null;
        }
    }

    /**
     * 拉取一页历史净值（按日期倒序，第 1 页是最新的）。
     *
     * @param code      基金代码
     * @param pageIndex 页码（从 1 开始）
     * @param pageSize  每页条数
     * @return 该页净值列表（已转为 {@link FundNav}，仅填充业务字段）与总条数
     * @throws Exception 网络异常或接口返回非 200 时抛出，交由上层处理/重试
     */
    public NavPage fetchNavHistory(String code, int pageIndex, int pageSize) throws Exception {
        String url = LSJZ_URL
                + "?fundCode=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&pageIndex=" + pageIndex
                + "&pageSize=" + pageSize;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Referer", fundConfig.getEastmoneyReferer())
                .timeout(Duration.ofSeconds(fundConfig.getRequestTimeoutSeconds()))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("历史净值接口异常状态码: " + resp.statusCode() + ", code=" + code);
        }

        JSONObject root = JSON.parseObject(resp.body());
        if (root == null) {
            throw new IllegalStateException("历史净值接口返回为空, code=" + code);
        }
        int total = root.getIntValue("TotalCount");
        JSONObject data = root.getJSONObject("Data");
        List<FundNav> list = new ArrayList<>();
        if (data != null) {
            JSONArray arr = data.getJSONArray("LSJZList");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    BigDecimal unitNav = parseDecimal(item.getString("DWJZ"));
                    if (unitNav == null) {
                        continue; // 没有单位净值的记录跳过（如货币基金/异常行）
                    }
                    FundNav nav = new FundNav();
                    nav.setFundCode(code);
                    nav.setNavDate(LocalDate.parse(item.getString("FSRQ")));
                    nav.setUnitNav(unitNav);
                    nav.setAccNav(parseDecimal(item.getString("LJJZ")));
                    nav.setGrowthRate(parseDecimal(item.getString("JZZZL")));
                    list.add(nav);
                }
            }
        }
        return new NavPage(list, total);
    }

    /**
     * 拉取基金画像（pingzhongdata）。该接口是 JS 文件，通过标量正则 + 括号配对提取 JSON 块解析。
     *
     * @param code 基金代码
     * @return 画像快照；调用或解析失败返回 {@code null}
     */
    public FundProfile fetchProfile(String code) {
        try {
            URI uri = URI.create(PINGZHONG_URL + URLEncoder.encode(code, StandardCharsets.UTF_8) + ".js");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", fundConfig.getEastmoneyReferer())
                    .timeout(Duration.ofSeconds(fundConfig.getRequestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("pingzhongdata 异常状态码: {}, code={}", resp.statusCode(), code);
                return null;
            }
            String body = resp.body();

            FundProfile p = new FundProfile();
            p.setFundCode(code);
            // 阶段涨幅：syl_1y=近1月, syl_3y=近3月, syl_6y=近6月, syl_1n=近1年
            p.setReturn1m(parseDecimal(extractScalar(body, "var syl_1y")));
            p.setReturn3m(parseDecimal(extractScalar(body, "var syl_3y")));
            p.setReturn6m(parseDecimal(extractScalar(body, "var syl_6y")));
            p.setReturn1y(parseDecimal(extractScalar(body, "var syl_1n")));
            p.setBuyRate(parseDecimal(extractScalar(body, "var fund_Rate")));
            p.setMinBuy(parseDecimal(extractScalar(body, "var fund_minsg")));

            // 同类排名百分位：走势最后一个点的值
            String simJson = extractBracketJson(body, "var Data_rateInSimilarPersent");
            if (simJson != null) {
                JSONArray arr = JSON.parseArray(simJson);
                if (arr != null && !arr.isEmpty()) {
                    JSONArray last = arr.getJSONArray(arr.size() - 1);
                    if (last != null && last.size() >= 2) {
                        p.setSimilarPercent(last.getBigDecimal(1));
                    }
                }
            }

            // 规模：series 最后一个 y + categories 最后一个季度末日期
            String scaleJson = extractBracketJson(body, "var Data_fluctuationScale");
            if (scaleJson != null) {
                JSONObject obj = JSON.parseObject(scaleJson);
                JSONArray series = obj.getJSONArray("series");
                JSONArray cats = obj.getJSONArray("categories");
                if (series != null && !series.isEmpty()) {
                    JSONObject lastS = series.getJSONObject(series.size() - 1);
                    if (lastS != null) {
                        p.setFundScale(lastS.getBigDecimal("y"));
                    }
                }
                if (cats != null && !cats.isEmpty()) {
                    String d = cats.getString(cats.size() - 1);
                    if (d != null && !d.isBlank()) {
                        try {
                            p.setScaleDate(LocalDate.parse(d));
                        } catch (Exception ignore) {
                            // 日期格式异常忽略
                        }
                    }
                }
            }

            // 基金经理：取第一个
            String mgrJson = extractBracketJson(body, "var Data_currentFundManager");
            if (mgrJson != null) {
                JSONArray arr = JSON.parseArray(mgrJson);
                if (arr != null && !arr.isEmpty()) {
                    JSONObject m = arr.getJSONObject(0);
                    if (m != null) {
                        p.setManagerName(m.getString("name"));
                        p.setManagerStar(m.getInteger("star"));
                        p.setManagerWorkTime(m.getString("workTime"));
                        p.setManagerSize(m.getString("fundSize"));
                        JSONObject power = m.getJSONObject("power");
                        if (power != null) {
                            p.setManagerScore(parseDecimal(power.getString("avr")));
                        }
                    }
                }
            }
            return p;
        } catch (Exception e) {
            log.error("拉取 pingzhongdata 失败 code={}", code, e);
            return null;
        }
    }

    /**
     * 拉取阶段收益、同类平均、沪深300对比与同类排名。
     *
     * @param code 基金代码
     * @return 阶段排名列表；调用失败返回空列表
     */
    public List<FundPeriodRank> fetchPeriodRanks(String code) {
        try {
            URI uri = URI.create(buildPeriodRankUrl(code));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .header("Referer", fundConfig.getEastmoneyReferer())
                    .timeout(Duration.ofSeconds(fundConfig.getRequestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("阶段排名接口异常状态码: {}, code={}", resp.statusCode(), code);
                return List.of();
            }

            JSONObject root = JSON.parseObject(resp.body());
            JSONArray arr = root == null ? null : root.getJSONArray("Datas");
            if (arr == null || arr.isEmpty()) {
                log.warn("阶段排名接口无数据 code={} errCode={} errMsg={} body={}",
                        code,
                        root == null ? null : root.getString("ErrCode"),
                        root == null ? null : root.getString("ErrMsg"),
                        bodySnippet(resp.body()));
                return List.of();
            }

            LocalDate asOf = parseExpansionDate(root);
            List<FundPeriodRank> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String periodCode = item.getString("title");
                if (periodCode == null || periodCode.isBlank()) {
                    continue;
                }

                FundPeriodRank rank = new FundPeriodRank();
                rank.setFundCode(code);
                rank.setPeriodCode(periodCode);
                rank.setPeriodName(periodName(periodCode));
                rank.setFundReturn(parseDecimal(item.getString("syl")));
                rank.setSimilarAverageReturn(parseDecimal(item.getString("avg")));
                rank.setBenchmarkReturn(parseDecimal(item.getString("hs300")));
                rank.setRankNo(parseInteger(item.getString("rank")));
                rank.setRankTotal(parseInteger(item.getString("sc")));
                rank.setAsOf(asOf);
                list.add(rank);
            }
            return list;
        } catch (Exception e) {
            log.error("拉取阶段排名失败 code={}", code, e);
            return List.of();
        }
    }

    /**
     * 提取 {@code var X = "value";} 或 {@code var X = value;} 形式的标量值。
     *
     * @param content JS 文本
     * @param varDecl 变量声明前缀，如 {@code "var syl_1n"}
     * @return 标量字符串，未找到返回 {@code null}
     */
    private static String extractScalar(String content, String varDecl) {
        Matcher m = Pattern.compile(Pattern.quote(varDecl) + "\\s*=\\s*\"?([^\";]*)\"?\\s*;").matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String buildPeriodRankUrl(String code) {
        String encoded = URLEncoder.encode(code, StandardCharsets.UTF_8);
        return MOBILE_API_URL + "FundMNPeriodIncrease"
                + "?AppVersion=6.3.8"
                + "&FCODE=" + encoded
                + "&MobileKey=3EA024C2-7F22-408B-95E4-383D38160FB3"
                + "&OSVersion=14.3"
                + "&deviceid=3EA024C2-7F22-408B-95E4-383D38160FB3"
                + "&passportid=3061335960830820"
                + "&plat=Iphone"
                + "&product=EFund"
                + "&version=6.3.6";
    }

    private static String bodySnippet(String body) {
        if (body == null) {
            return null;
        }
        String text = body.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static LocalDate parseExpansionDate(JSONObject root) {
        if (root == null) {
            return null;
        }
        Object expansion = root.get("Expansion");
        if (expansion instanceof JSONObject obj) {
            return parseDate(obj.getString("TIME"));
        }
        return parseDate(expansion == null ? null : expansion.toString());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "--".equals(value.trim())) {
            return null;
        }
        String text = value.trim();
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String periodName(String code) {
        return switch (code) {
            case "Z" -> "近1周";
            case "Y" -> "近1月";
            case "3Y" -> "近3月";
            case "6Y" -> "近6月";
            case "1N" -> "近1年";
            case "2N" -> "近2年";
            case "3N" -> "近3年";
            case "5N" -> "近5年";
            case "JN" -> "今年以来";
            case "LN" -> "成立以来";
            default -> code;
        };
    }

    /**
     * 从 {@code var X = [...]} 或 {@code var X = {...}} 中提取完整的 JSON 数组/对象（按括号配对，
     * 正确处理嵌套与字符串内的括号）。
     *
     * @param content JS 文本
     * @param varDecl 变量声明前缀，如 {@code "var Data_currentFundManager"}
     * @return JSON 片段字符串，未找到返回 {@code null}
     */
    private static String extractBracketJson(String content, String varDecl) {
        int idx = content.indexOf(varDecl);
        if (idx < 0) {
            return null;
        }
        int eq = content.indexOf('=', idx);
        if (eq < 0) {
            return null;
        }
        int start = -1;
        char open = 0;
        char close = 0;
        for (int i = eq + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[' || c == '{') {
                start = i;
                open = c;
                close = (c == '[') ? ']' : '}';
                break;
            }
            if (!Character.isWhitespace(c)) {
                return null; // 不是数组/对象（标量），交给 extractScalar
            }
        }
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inStr) {
                if (c == quote && content.charAt(i - 1) != '\\') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                quote = c;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 把接口返回的字符串安全转为 {@link BigDecimal}，空串/非数字返回 {@code null}。
     */
    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 基金基本信息。
     *
     * @param code 基金代码
     * @param name 基金名称
     * @param type 基金类型（可能为 null）
     */
    public record FundBasic(String code, String name, String type) {
    }

    /**
     * 一页历史净值结果。
     *
     * @param list       本页净值列表
     * @param totalCount 该基金历史净值总条数
     */
    public record NavPage(List<FundNav> list, int totalCount) {
    }
}
