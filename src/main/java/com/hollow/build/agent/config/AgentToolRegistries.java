package com.hollow.build.agent.config;

import com.hollow.build.agent.alias.AliasTool;
import com.hollow.build.agent.core.ToolRegistry;
import com.hollow.build.agent.database.DatabaseTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 给每个 agent 装配独立的 {@link ToolRegistry}。
 *
 * <p>关键点：通过让 Spring 注入 {@code List<DatabaseTool>} / {@code List<AliasTool>}，
 * 各 agent 只看到属于自己分组的工具。新增 agent 只要：
 * <ol>
 *   <li>定义新的标记接口（如 {@code XxxTool extends Tool}）</li>
 *   <li>对应工具实现该接口、加 @Component</li>
 *   <li>这里再加一个 @Bean</li>
 * </ol>
 */
@Configuration
public class AgentToolRegistries {

    @Bean
    public ToolRegistry databaseToolRegistry(List<DatabaseTool> tools) {
        return new ToolRegistry(tools);
    }

    @Bean
    public ToolRegistry aliasToolRegistry(List<AliasTool> tools) {
        return new ToolRegistry(tools);
    }
}
