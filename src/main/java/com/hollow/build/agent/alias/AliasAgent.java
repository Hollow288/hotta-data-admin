package com.hollow.build.agent.alias;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.agent.config.AgentProperties;
import com.hollow.build.agent.config.AgentSwitches;
import com.hollow.build.agent.core.AbstractAgent;
import com.hollow.build.agent.core.ToolRegistry;
import com.hollow.build.ai.client.openai.OpenAiChatClient;
import com.hollow.build.agent.log.AgentLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final Set<String> VALID_TYPES = Set.of("武器", "意志", "源器");

    public AliasAgent(OpenAiChatClient openAiChatClient,
                      @Qualifier("aliasToolRegistry") ToolRegistry toolRegistry,
                      AgentProperties agentProperties,
                      AgentLogService agentLogService,
                      AgentSwitches agentSwitches) {
        super(openAiChatClient, toolRegistry, agentProperties, agentLogService, agentSwitches);
    }

    @Override
    public String agentName() {
        return "alias";
    }

    @Override
    public String routerDescription() {
        return """
                仅处理游戏物品（武器 / 意志 / 源器）相关问题。
                输入对象是物品的口语别名或正式名，无论用户是直接提及（"大红莲是什么"）
                还是带查询动词（"查一下护盾源器"），只要谈的是游戏物品就属于这里。
                """;
    }

    @Override
    public String routerKeywords() {
        return "出现游戏物品类型（武器 / 意志 / 源器）或物品口语别名（如\"大红莲\"\"赤风\"\"护盾源器\"\"三刀哥\"等）";
    }

    @Override
    public List<RouterExample> routerExamples() {
        return List.of(
                new RouterExample("查一下护盾源器", "\"护盾\"是源器的口语别名"),
                new RouterExample("大红莲是哪把武器", "\"大红莲\"是武器的口语别名"),
                new RouterExample("三刀哥的技能描述", "口语别名优先做正式名识别")
        );
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

                matches 里 matchedBy 字段的可信度顺序（从高到低）：
                  exact_canonical / exact_alias       —— 精确命中，直接采用
                  contains_canonical / contains_alias —— 子串命中，可直接采用
                  fuzzy_pinyin                        —— 同音/谐音降级（如 "洪莲" → "红莲"）
                  fuzzy_edit1                         —— 字形相近降级（编辑距离 ≤ 1）

                选择规则：
                  - 优先选可信度最高的那一类（exact > contains > fuzzy_pinyin > fuzzy_edit1）。
                  - 同一可信度内有多条时，结合用户原话、社区常用度自行判断挑最像的那一条，
                    无需回问用户。例如 "红莲" 同时命中 "赤风"（大红莲）和 "红莲刃"，
                    默认采用更主流的 "赤风"。

                注意：
                  - 你**不能凭空编造正式名**，所有 value 必须来自 search_alias 返回的 matches。
                  - 你仍可以在调用 search_alias 之前主动纠正用户输入（错别字、英文混拼、口误），
                    工具侧的 fuzzy 兜底只是双保险，不是让你停止纠错。
                """;
    }

    @Override
    protected Object answerData(String reply) {
        String json = extractJsonObject(reply);
        if (json == null) {
            return null;
        }

        try {
            Map<String, Object> data = JSON.parseObject(
                    json, new TypeReference<LinkedHashMap<String, Object>>() {});
            Object type = data.get("type");
            if (type != null && !VALID_TYPES.contains(type.toString())) {
                return null;
            }
            return data;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    protected String answerText(String reply, Object answerData) {
        if (answerData instanceof Map<?, ?> data) {
            Object value = data.get("value");
            if (value != null) {
                return value.toString();
            }
            Object reason = data.get("reason");
            return reason == null ? "未在别名库中找到匹配项" : reason.toString();
        }
        return super.answerText(reply, answerData);
    }

    private static String extractJsonObject(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        String trimmed = reply.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return trimmed.substring(start, end + 1);
    }
}
