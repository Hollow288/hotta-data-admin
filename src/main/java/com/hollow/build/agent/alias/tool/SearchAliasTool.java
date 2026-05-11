package com.hollow.build.agent.alias.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.alias.AliasDataLoader;
import com.hollow.build.agent.alias.AliasTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具 2：在指定分类（必填）里按别名查正式名。
 *
 * <p>匹配策略：
 * <ul>
 *   <li>正式名/别名做 trim 后**忽略大小写**比较</li>
 *   <li>支持精确匹配 + 包含匹配（query 是别名/正式名的子串，或反之）</li>
 *   <li>同一个 query 可能命中多个正式名 —— 全部返回，由 AI 决定怎么用</li>
 * </ul>
 */
@Component
public class SearchAliasTool implements AliasTool {

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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("query", query);
        result.put("matches", matches);
        return JSON.toJSONString(result);
    }

    /**
     * 返回命中方式：exact_canonical / exact_alias / contains_canonical / contains_alias / null。
     */
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

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
