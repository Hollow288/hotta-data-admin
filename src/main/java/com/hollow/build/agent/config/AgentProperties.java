package com.hollow.build.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 相关配置项，绑定 application.yml 里 com.hollow.agent.* 节点。
 *
 * 重点是 allowedTables —— 一个简单 Agent 给数据库做的"安全护栏"。
 * 任何不在该白名单里的表，工具都会拒绝访问。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.agent")
public class AgentProperties {

    /** Agent 主循环最多跑几轮，防止无限调用工具 */
    private int maxIterations = 6;

    /** 一次 query_table 最多返回多少行 */
    private int maxQueryLimit = 50;

    /** 允许 AI 访问的表名白名单 */
    private List<String> allowedTables = List.of("blog_posts", "event_news", "role");
}
