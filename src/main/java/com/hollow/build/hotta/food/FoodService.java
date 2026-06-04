package com.hollow.build.hotta.food;


import java.util.List;

/**
 * 食物服务接口，提供食物的查询功能
 */
public interface FoodService {

    /**
     * 获取所有食物列表
     *
     * @return 所有食物的列表
     */
    List<Food> getAllFood();

    /**
     * 根据食物唯一标识获取食物详情
     *
     * @param itemKey 食物的唯一标识键
     * @return 对应的食物对象
     */
    Food getFoodByKey(String itemKey);

    /**
     * 查询食物列表（带参数过滤）
     *
     * @return 食物列表DTO
     */
    List<FoodListDto> getFoodByParams();
}
