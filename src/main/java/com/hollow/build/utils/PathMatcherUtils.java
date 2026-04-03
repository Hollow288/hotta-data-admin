package com.hollow.build.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 路径匹配工具类，基于 {@link AntPathMatcher} 提供单路径和多路径匹配能力。
 */
@Component
public class PathMatcherUtils {

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 判断 path 是否匹配 pattern（支持 Ant 风格通配符）
     *
     * @param pattern 路径匹配模式
     * @param path 待匹配的请求路径
     * @return 匹配成功返回 true，否则返回 false
     */
    public boolean match(String pattern, String path) {
        return antPathMatcher.match(pattern, path);
    }

    /**
     * 判断 path 是否匹配任意 pattern 集合
     *
     * @param patterns 路径模式集合
     * @param path 待匹配的请求路径
     * @return 任意一个模式匹配成功返回 true，否则返回 false
     */
    public boolean matchAny(List<String> patterns, String path) {
        if (patterns == null) return false;
        return patterns.stream().anyMatch(p -> antPathMatcher.match(p, path));
    }
}

