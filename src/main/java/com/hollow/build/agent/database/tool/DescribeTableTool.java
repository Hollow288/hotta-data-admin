package com.hollow.build.agent.database.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.database.DatabaseTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具 2：返回某张表的列结构（列名、类型、是否可空、注释）。
 *
 * 数据来源是 MySQL 自带的 INFORMATION_SCHEMA.COLUMNS。
 * 表名先经白名单校验再使用，避免任意表泄露。
 */
@Component
public class DescribeTableTool implements DatabaseTool {

    private final JdbcTemplate jdbcTemplate;
    private final AgentProperties agentProperties;

    public DescribeTableTool(JdbcTemplate jdbcTemplate, AgentProperties agentProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "describe_table";
    }

    @Override
    public String description() {
        return "查看某张表的列结构（列名、类型、是否可空、注释）。AI 不确定字段时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "table", Map.of(
                                "type", "string",
                                "description", "要查看结构的表名，必须在白名单中"
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

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME AS name, " +
                        "       COLUMN_TYPE AS type, " +
                        "       IS_NULLABLE AS nullable, " +
                        "       COLUMN_COMMENT AS comment " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION",
                table
        );

        return JSON.toJSONString(Map.of(
                "table", table,
                "columns", columns
        ));
    }
}
