package com.hollow.build.utils;

import org.springframework.beans.BeanUtils;

/**
 * DTO 映射工具类
 * <p>
 * 提供通用的实体与 DTO 之间的转换方法，基于 Spring BeanUtils 实现属性拷贝。
 * </p>
 */
public class DtoMapperUtil {

    /**
     * 通用实体转DTO方法
     * @param source 源实体
     * @param targetClass 目标DTO类
     * @param <S> 源实体类型
     * @param <T> 目标DTO类型
     * @return DTO实例
     */
    public static <S, T> T map(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("映射失败", e);
        }
    }
}
