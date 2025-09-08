package com.hollow.build.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
public class PathMatcherUtils {

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 判断 path 是否匹配 pattern（支持 Ant 风格通配符）
     */
    public boolean match(String pattern, String path) {
        return antPathMatcher.match(pattern, path);
    }

    /**
     * 判断 path 是否匹配任意 pattern 集合
     */
    public boolean matchAny(List<String> patterns, String path) {
        if (patterns == null) return false;
        return patterns.stream().anyMatch(p -> antPathMatcher.match(p, path));
    }
}

