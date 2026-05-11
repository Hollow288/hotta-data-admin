package com.hollow.build.agent.database.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.database.DatabaseTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具 3：用受限的方式查询某张表的数据。
 *
 * <p>设计原则：<b>不让 AI 直接写 SQL</b>，只让它选：
 * <ul>
 *   <li>查哪张表（必须在白名单）</li>
 *   <li>可选：用哪个列做等值过滤、过滤值是什么</li>
 *   <li>可选：返回多少行（受 maxQueryLimit 上限约束）</li>
 * </ul>
 *
 * <p>护栏：
 * <ul>
 *   <li>表名只能取自白名单</li>
 *   <li>列名用正则限定 [A-Za-z_][A-Za-z0-9_]* —— 避免 SQL 注入</li>
 *   <li>过滤值通过 PreparedStatement 参数化绑定</li>
 *   <li>limit 是 int，并被钳制到 [1, maxQueryLimit]</li>
 * </ul>
 *
 * 真实项目里给 AI 暴露数据库时，这是常见护栏写法。
 */
@Component
public class QueryTableTool implements DatabaseTool {

    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final AgentProperties agentProperties;

    public QueryTableTool(JdbcTemplate jdbcTemplate, AgentProperties agentProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "query_table";
    }

    @Override
    public String description() {
        return "查询某张表的数据。可指定一个等值过滤条件（whereField = whereValue）和返回行数。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "table", Map.of(
                                "type", "string",
                                "description", "要查询的表名，必须在白名单中"
                        ),
                        "whereField", Map.of(
                                "type", "string",
                                "description", "可选：用于等值过滤的列名"
                        ),
                        "whereValue", Map.of(
                                "type", "string",
                                "description", "可选：whereField 等于的值"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "返回的最大行数，默认 10，最大不超过 maxQueryLimit"
                        )
                ),
                "required", List.of("table")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String table = (String) arguments.get("table");
        if (table == null || !agentProperties.getAllowedTables().contains(table)) {
            return JSON.toJSONString(Map.of("error", "表 " + table + " 不在允许访问的白名单内"));
        }

        String whereField = (String) arguments.get("whereField");
        Object whereValue = arguments.get("whereValue");
        if (whereField != null && !IDENTIFIER.matcher(whereField).matches()) {
            return JSON.toJSONString(Map.of("error", "非法的列名: " + whereField));
        }

        Object limitArg = arguments.get("limit");
        int limit = 10;
        if (limitArg instanceof Number n) {
            limit = n.intValue();
        } else if (limitArg instanceof String s) {
            try { limit = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        limit = Math.max(1, Math.min(limit, agentProperties.getMaxQueryLimit()));

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<Object> params = new ArrayList<>();
        if (whereField != null && whereValue != null) {
            sql.append(" WHERE ").append(whereField).append(" = ?");
            params.add(whereValue);
        }
        sql.append(" LIMIT ").append(limit);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        return JSON.toJSONString(Map.of(
                "table", table,
                "rowCount", rows.size(),
                "rows", rows
        ));
    }
}
