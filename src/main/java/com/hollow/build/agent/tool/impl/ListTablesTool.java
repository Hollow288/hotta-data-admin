package com.hollow.build.agent.tool.impl;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具 1：列出 Agent 允许访问的所有表。
 *
 * AI 通常会先调用它"看看自己能查什么"，再决定下一步。
 */
@Component
public class ListTablesTool implements Tool {

    private final AgentProperties agentProperties;

    public ListTablesTool(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "list_tables";
    }

    @Override
    public String description() {
        return "列出当前 Agent 允许访问的所有数据库表名。无需参数。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        // 没有参数，但仍要返回一个空的 object schema
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return JSON.toJSONString(Map.of(
                "tables", agentProperties.getAllowedTables()
        ));
    }
}
