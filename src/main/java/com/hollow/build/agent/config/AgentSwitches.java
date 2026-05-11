package com.hollow.build.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运营开关。
 *
 * <p>状态只放在内存（{@link ConcurrentHashMap}）。启动时从
 * {@link AgentProperties#getEnabledAgents()} 灌入默认值，运行期间允许通过
 * Admin 接口动态翻转；<b>进程重启后会回到 yml 默认值</b>，不做持久化。
 *
 * <p>没在 yml 里显式列出的 agent 名按"开启"处理，避免新加 agent 时忘了配置导致整个不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSwitches {

    private final AgentProperties agentProperties;

    private final Map<String, Boolean> states = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, Boolean> defaults = agentProperties.getEnabledAgents();
        if (defaults != null) {
            states.putAll(defaults);
        }
        log.info("Agent 开关初始化完成: {}", states);
    }

    /** 默认 true。未知 agent 名也按开启处理。 */
    public boolean isEnabled(String agentName) {
        return states.getOrDefault(agentName, Boolean.TRUE);
    }

    /** 翻转开关；返回翻转之前的状态（true=之前开启）。 */
    public boolean setEnabled(String agentName, boolean enabled) {
        Boolean prev = states.put(agentName, enabled);
        log.info("Agent 开关变更: {} {} -> {}", agentName, prev, enabled);
        return prev == null ? Boolean.TRUE : prev;
    }

    /** 不可变快照，给 admin 接口列清单用，保留插入顺序。 */
    public Map<String, Boolean> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }
}
