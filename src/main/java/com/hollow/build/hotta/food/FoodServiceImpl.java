package com.hollow.build.hotta.food;

import com.hollow.build.hotta.food.FoodListDto;
import com.hollow.build.hotta.food.Food;
import com.hollow.build.hotta.food.FoodRepository;
import com.hollow.build.hotta.food.FoodService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 食物服务实现类，负责查询食物数据并补全图标访问地址。
 */
@Service
@RequiredArgsConstructor
public class FoodServiceImpl implements FoodService {

    private final FoodRepository foodRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;
    
    /**
     * 查询全部食物信息，并补全图标访问地址。
     *
     * @return 全部食物列表
     */
    @Override
    @Cacheable(value = "food_all")
    public List<Food> getAllFood() {
        return foodRepository.findAll().stream()
                .peek(food -> {
                    food.setFoodIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));
                }).toList();
    }

    /**
     * 根据食物唯一键查询详情，并补全图标访问地址。
     *
     * @param itemKey 食物唯一标识
     * @return 食物详情，未找到时返回 null
     */
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

    /**
     * 查询用于列表展示的食物简要信息。
     *
     * @return 食物列表 DTO 集合
     */
    @Override
    @Cacheable(value = "food_list")
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
