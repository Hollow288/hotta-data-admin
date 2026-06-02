package com.hollow.build.agent.alias.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.alias.AliasDataLoader;
import com.hollow.build.agent.alias.AliasTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具 1：列出所有可用的分类（武器 / 意志 / 源器）。
 *
 * <p>AI 第一步通常先调它，确认有哪几种类型可选，避免编造。
 */
@Component
public class
ListCategoriesTool implements AliasTool {

    private final AliasDataLoader dataLoader;

    public ListCategoriesTool(AliasDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public String name() {
        return "list_categories";
    }

    @Override
    public String description() {
        return "列出别名库里所有可用的分类（type），例如 武器 / 意志 / 源器。无需参数。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return JSON.toJSONString(Map.of("categories", dataLoader.categories()));
    }
}
