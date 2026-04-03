package com.hollow.build.service;

import com.hollow.build.entity.mongo.Fashion;

import java.util.List;

/**
 * 时装服务接口，提供时装的查询功能
 */
public interface FashionService {

    /**
     * 获取所有时装列表
     *
     * @return 所有时装的列表
     */
    List<Fashion> getAllFashion();

    /**
     * 根据时装唯一标识获取时装详情
     *
     * @param itemKey 时装的唯一标识键
     * @return 对应的时装对象
     */
    Fashion getFashionByKey(String itemKey);
}
