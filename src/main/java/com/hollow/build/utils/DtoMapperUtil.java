package com.hollow.build.utils;

import org.springframework.beans.BeanUtils;

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
