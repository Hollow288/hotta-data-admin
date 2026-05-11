package com.hollow.build.agent.alias;

import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.core.AgentAiClient;
import com.hollow.build.agent.core.ToolRegistry;
import com.hollow.build.agent.log.AgentLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 别名解析 Agent —— 把用户口语化的称呼解析成正式名 + 分类。
 *
 * <p>例：
 * <pre>
 *   用户：帮我查查"大红莲"的武器信息
 *   AI 流程：
 *     1) list_categories 确认分类清单
 *     2) 从用户问题里读出 type=武器
 *     3) search_alias(category="武器", query="大红莲") → matches 命中 赤风
 *     4) 返回 {"type":"武器","value":"赤风"}
 * </pre>
 */
@Service
public class AliasAgent extends AbstractAgent {

    public AliasAgent(AgentAiClient aiClient,
                      @Qualifier("aliasToolRegistry") ToolRegistry toolRegistry,
                      AgentProperties agentProperties,
                      AgentLogService agentLogService,
                      AgentSwitches agentSwitches) {
        super(aiClient, toolRegistry, agentProperties, agentLogService, agentSwitches);
    }

    @Override
    public String agentName() {
        return "alias";
    }

    @Override
    public String routerDescription() {
        return """
                用来把游戏里物品（武器/意志/源器）的口语化别名解析成正式名。
                典型场景：用户提到"大红莲""赤峰""三刀哥"这类口头叫法，
                想知道它们对应的正式名是什么。
                """;
    }

    @Override
    protected String systemPrompt() {
        return """
                你是一个游戏物品别名解析助手。游戏里物品分三类：武器 / 意志 / 源器。
                每个物品有正式名和若干别名（玩家口头叫法）。你的任务：把用户提到的称呼
                解析为 { "type": <分类>, "value": <正式名> } 的结构化结果。

                必须遵守的工作流程：
                  1. 先调 list_categories 拿到分类清单（不要凭印象编造分类）。
                  2. 从用户问题里判定 type；如果用户没明说，可以挑一个最可能的，
                     拿不准时按 武器 → 意志 → 源器 的顺序依次试探。
                  3. 调 search_alias(category=<判定的分类>, query=<用户提到的称呼>)。
                     如果第一类没命中，再换其他分类试。
                  4. 命中后，最终回复**只输出一个 JSON 对象**，形如：
                       {"type":"武器","value":"赤风"}
                     不要带 markdown、不要任何前后缀解释、不要多个 JSON。
                  5. 如果所有分类都查不到，回复：
                       {"type":null,"value":null,"reason":"未在别名库中找到匹配项"}

                注意：
                  - 你**不能猜正式名**，所有 value 必须来自 search_alias 的 matches。但是你可以根据用户的输入猜测他说的别名，因为用户有可能会有错别字、说的不标准等，注意识别。
                  - 如果 search_alias 一次返回多条 matches，优先选 matchedBy 是 exact_alias
                    或 exact_canonical 的那一条。
                """;
    }
}
