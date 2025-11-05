package com.hollow.build.service.impl;

import com.hollow.build.dto.FoodListDto;
import com.hollow.build.entity.mongo.Food;
import com.hollow.build.repository.mongo.FoodRepository;
import com.hollow.build.service.FoodService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodServiceImpl implements FoodService {

    private final FoodRepository foodRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;
    
    @Override
    @Cacheable(value = "food_all")
    public List<Food> getAllFood() {
        return foodRepository.findAll().stream()
                .peek(food -> {
                    food.setFoodIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));
                }).toList();
    }

    @Override
    @Cacheable(value = "food", key = "#itemKey")
    public Food getFoodByKey(String itemKey) {
        Food food = foodRepository.findByFoodKey(itemKey);

        if (food == null) {
            return null;
        }

        // 拼接主图标
        food.setFoodIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));

        return food;
    }

    @Override
    public List<FoodListDto> getFoodByParams() {
        Query query = new Query();

        query.fields()
                .include("foodKey")
                .include("foodName")
                .include("foodIcon");

        List<FoodListDto> foodSearchList = mongoTemplate.find(query, FoodListDto.class, "food");

        foodSearchList.forEach(foodListDto -> {
            foodListDto.setFoodIcon(minioUtil.fileUrlEncoderChance(foodListDto.getFoodIcon(),"hotta"));
        });
        return foodSearchList;
    }
}
