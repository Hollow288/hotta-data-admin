package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Food;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends MongoRepository<Food, String> {
    // 按武器Key查询
    Food findByFoodKey(String foodKey);
}
