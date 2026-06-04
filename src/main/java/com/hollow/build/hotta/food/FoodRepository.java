package com.hollow.build.hotta.food;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 食物（Food）MongoDB 数据访问接口。
 */
@Repository
public interface FoodRepository extends MongoRepository<Food, String> {

    /**
     * 根据食物唯一标识键查询食物信息。
     *
     * @param foodKey 食物唯一标识键
     * @return 匹配的食物对象，未找到时返回 null
     */
    Food findByFoodKey(String foodKey);
}
