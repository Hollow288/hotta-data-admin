package com.hollow.build.agent.alias;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hollow.build.agent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 启动时加载别名表，缓存到内存。
 *
 * <p>读取顺序：先看 {@link AgentProperties#getAliasFilePath()} 指向的外部文件，
 * 文件不存在则回退到 classpath:agent/alias.json。这样既能让运维通过 docker volume
 * 覆盖打包进 JAR 的默认数据，本地开发又不用额外准备文件。
 *
 * <p>结构：
 * <pre>
 *  {
 *    "武器":  { "赤风": ["大红莲", "赤峰", ...], ... },
 *    "意志":  { ... },
 *    "源器":  { ... }
 *  }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliasDataLoader {

    private static final String CLASSPATH = "agent/alias.json";

    private final AgentProperties agentProperties;

    /** category -> (canonical -> aliases)。LinkedHashMap 保留 JSON 中的顺序方便阅读。 */
    private Map<String, Map<String, List<String>>> data = Map.of();

    @PostConstruct
    public void load() {
        String externalPath = agentProperties.getAliasFilePath();
        Path ext = externalPath == null ? null : Paths.get(externalPath);
        boolean useExternal = ext != null && Files.isRegularFile(ext);

        try (InputStream in = useExternal
                ? Files.newInputStream(ext)
                : new ClassPathResource(CLASSPATH).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            this.data = JSON.parseObject(json, new TypeReference<Map<String, Map<String, List<String>>>>() {});
            int total = data.values().stream().mapToInt(Map::size).sum();
            log.info("alias.json 加载完成（来源：{}）: {} 个分类、共 {} 个正式名",
                    useExternal ? ext.toAbsolutePath() : "classpath:" + CLASSPATH,
                    data.size(), total);
        } catch (Exception e) {
            log.error("加载别名表失败（外部路径={}，classpath={}），AliasAgent 将无法工作",
                    externalPath, CLASSPATH, e);
            this.data = Map.of();
        }
    }

    public List<String> categories() {
        return List.copyOf(data.keySet());
    }

    /** 给定 category，返回 canonical -> aliases 全表。category 不存在则返回空 map。 */
    public Map<String, List<String>> ofCategory(String category) {
        Map<String, List<String>> m = data.get(category);
        return m == null ? Map.of() : m;
    }

    public Map<String, Map<String, List<String>>> raw() {
        return data;
    }
}
