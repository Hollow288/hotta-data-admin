package com.hollow.build.agent.alias.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.alias.AliasDataLoader;
import com.hollow.build.agent.alias.AliasTool;
import com.hollow.build.agent.alias.util.EditDistance;
import com.hollow.build.agent.alias.util.PinyinUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具 2：在指定分类（必填）里按别名查正式名。
 *
 * <p>匹配分两轮，命中分等级（{@code matchedBy} 字段）：
 * <ol>
 *   <li><b>精确/包含</b>：原有规则。
 *     <ul>
 *       <li>{@code exact_canonical} / {@code exact_alias}：忽略大小写后完全相等</li>
 *       <li>{@code contains_canonical} / {@code contains_alias}：互为子串</li>
 *     </ul>
 *   </li>
 *   <li><b>降级兜底（fuzzy）</b>：第一轮无任何命中时才触发，专门处理错别字/同音字。
 *     <ul>
 *       <li>{@code fuzzy_pinyin}：拼音按音节相等或互为音节子串
 *           （如 "洪莲" → "红莲" → 命中 "大红莲"）</li>
 *       <li>{@code fuzzy_edit1}：编辑距离 ≤ 1（如 "赤峰" → "赤风"）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>AI 侧应该把 {@code fuzzy_*} 视为"降级候选"：单条直接用，多条优先回问用户。
 */
@Component
public class SearchAliasTool implements AliasTool {

    private static final int EDIT_THRESHOLD = 1;

    private final AliasDataLoader dataLoader;

    public SearchAliasTool(AliasDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public String name() {
        return "search_alias";
    }

    @Override
    public String description() {
        return """
                在指定分类下，根据别名（或正式名片段）查它对应的正式名。
                参数：
                  - category: 分类名，必须先用 list_categories 看清单后再传，例如 "武器"。
                  - query:    用户提到的称呼，例如 "大红莲"。
                返回 matches 数组，每条含 canonical（正式名）和 matchedBy（命中方式）。
                matchedBy 可能取值（按可信度从高到低）：
                  exact_canonical / exact_alias       —— 精确命中
                  contains_canonical / contains_alias —— 子串命中
                  fuzzy_pinyin                        —— 同音/谐音降级命中（如 "洪莲" → "红莲"）
                  fuzzy_edit1                         —— 字形相近降级命中（编辑距离 ≤ 1，如 "赤峰" → "赤风"）
                若 matches 为空，说明该 category 下没有匹配，可以换 category 再试。
                """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> categoryProp = new LinkedHashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("description", "分类名，例如 武器 / 意志 / 源器");

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "用户提到的称呼（可能是别名也可能是正式名）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("category", categoryProp);
        properties.put("query", queryProp);

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("category", "query")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String category = stringArg(arguments, "category");
        String query = stringArg(arguments, "query");

        if (category == null || query == null) {
            return JSON.toJSONString(Map.of("error", "category 和 query 都是必填参数"));
        }

        Map<String, List<String>> bucket = dataLoader.ofCategory(category);
        if (bucket.isEmpty()) {
            return JSON.toJSONString(Map.of(
                    "error", "未知分类: " + category,
                    "availableCategories", dataLoader.categories()
            ));
        }

        String needle = query.trim().toLowerCase();
        List<Map<String, Object>> matches = exactAndContainsMatch(needle, bucket);

        // 第一轮没命中再走 fuzzy，避免对精确命中的请求也跑一遍拼音/编辑距离白白消耗。
        if (matches.isEmpty()) {
            matches = fuzzyMatch(needle, bucket);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("query", query);
        result.put("matches", matches);
        return JSON.toJSONString(result);
    }

    /** 第一轮：精确/包含。和旧实现行为完全一致。 */
    private static List<Map<String, Object>> exactAndContainsMatch(
            String needle, Map<String, List<String>> bucket) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : bucket.entrySet()) {
            String canonical = e.getKey();
            String matchedBy = matchKind(needle, canonical, e.getValue());
            if (matchedBy != null) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("canonical", canonical);
                hit.put("matchedBy", matchedBy);
                matches.add(hit);
            }
        }
        return matches;
    }

    /** 第二轮：拼音相同 / 编辑距离 ≤ 1。同一个 canonical 只保留一条（优先 pinyin）。 */
    private static List<Map<String, Object>> fuzzyMatch(
            String needle, Map<String, List<String>> bucket) {
        List<Map<String, Object>> matches = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> e : bucket.entrySet()) {
            String canonical = e.getKey();
            List<String> aliases = e.getValue();

            if (pinyinHit(needle, canonical, aliases)) {
                if (added.add(canonical)) {
                    matches.add(Map.of("canonical", canonical, "matchedBy", "fuzzy_pinyin"));
                }
                continue;
            }
            if (editHit(needle, canonical, aliases)) {
                if (added.add(canonical)) {
                    matches.add(Map.of("canonical", canonical, "matchedBy", "fuzzy_edit1"));
                }
            }
        }
        return matches;
    }

    /** 返回命中方式：exact_canonical / exact_alias / contains_canonical / contains_alias / null。 */
    private static String matchKind(String needle, String canonical, List<String> aliases) {
        String c = canonical.toLowerCase();
        if (c.equals(needle)) return "exact_canonical";
        for (String a : aliases) {
            if (a.toLowerCase().equals(needle)) return "exact_alias";
        }
        if (c.contains(needle) || needle.contains(c)) return "contains_canonical";
        for (String a : aliases) {
            String al = a.toLowerCase();
            if (al.contains(needle) || needle.contains(al)) return "contains_alias";
        }
        return null;
    }

    private static boolean pinyinHit(String needle, String canonical, List<String> aliases) {
        if (PinyinUtil.syllablesContain(needle, canonical)) return true;
        for (String a : aliases) {
            if (PinyinUtil.syllablesContain(needle, a)) return true;
        }
        return false;
    }

    private static boolean editHit(String needle, String canonical, List<String> aliases) {
        if (EditDistance.atMost(needle, canonical.toLowerCase(), EDIT_THRESHOLD)) return true;
        for (String a : aliases) {
            if (EditDistance.atMost(needle, a.toLowerCase(), EDIT_THRESHOLD)) return true;
        }
        return false;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
