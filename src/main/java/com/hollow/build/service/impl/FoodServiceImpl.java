package com.hollow.build.service.impl;

import com.hollow.build.entity.mongo.Food;
import com.hollow.build.repository.mongo.FoodRepository;
import com.hollow.build.service.FoodService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodServiceImpl implements FoodService {

    private final FoodRepository foodRepository;

    private final MinioUtil minioUtil;
    
    @Override
    public List<Food> getAllFood() {
        return foodRepository.findAll().stream()
                .peek(food -> {
                    food.setFoodIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));
                }).toList();
    }

    @Override
    public Food getFoodByKey(String itemKey) {
        Food food = foodRepository.findByFoodKey(itemKey);

        if (food == null) {
            return null;
        }

        // 拼接主图标
        food.setFoodIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));

        return food;
    }
}
