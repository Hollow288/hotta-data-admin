package com.hollow.build.service;

import com.hollow.build.entity.mongo.Food;

import java.util.List;

public interface FoodService {
    List<Food> getAllFood();

    Food getFoodByKey(String itemKey);
}
