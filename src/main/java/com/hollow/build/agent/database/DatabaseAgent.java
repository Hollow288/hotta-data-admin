package com.hollow.build.agent.database;

import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.core.AgentAiClient;
import com.hollow.build.agent.core.ToolRegistry;
import com.hollow.build.agent.log.AgentLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据库查询 Agent —— 老规矩：用 list_tables / describe_table / query_table 去回答用户问题。
 *
 * <p>所有的循环逻辑都在 {@link AbstractAgent}，这里只配置"我是谁"。
 */
@Service
public class DatabaseAgent extends AbstractAgent {

    public DatabaseAgent(AgentAiClient aiClient,
                         @Qualifier("databaseToolRegistry") ToolRegistry toolRegistry,
                         AgentProperties agentProperties,
                         AgentLogService agentLogService,
                         AgentSwitches agentSwitches) {
        super(aiClient, toolRegistry, agentProperties, agentLogService, agentSwitches);
    }

    @Override
    public String agentName() {
        return "database";
    }

    @Override
    public String routerDescription() {
        return """
                仅处理数据库表（blog_posts / event_news / role 等白名单表）相关问题。
                输入对象是表本身或表里的记录，常见动作是查询、统计、列表、过滤、求最新一条 / 有几条等。
                """;
    }

    @Override
    public String routerKeywords() {
        return "出现表名（user / blog_posts / event_news / role 等）、字段、SQL、行数、\"第 N 条 / 第 N 行\"、\"有几条\"、\"最新一条\"等数据库术语";
    }

    @Override
    public List<RouterExample> routerExamples() {
        return List.of(
                new RouterExample("查一下 user 表的第一行数据", "出现\"user 表\"这个表名信号"),
                new RouterExample("blog_posts 有多少条", "出现表名 + \"有几条\""),
                new RouterExample("最新一条活动资讯是啥", "指向 event_news 表的记录")
        );
    }

    @Override
    protected String systemPrompt() {
        return """
                你是一个数据库查询助手。你只能通过提供的工具来访问数据库，不能编造数据。
                典型流程：
                  1. 先用 list_tables 看看能查哪些表；
                  2. 用 describe_table 了解目标表的字段；
                  3. 用 query_table 拿到数据（必要时加 whereField/whereValue 过滤）；
                  4. 用自然语言把结果总结给用户，并附上你查到的关键字段。
                如果用户的问题不能通过这些工具回答，请直接说明无法处理，不要瞎编。
                """;
    }
}
