package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.entity.mongo.Food;
import com.hollow.build.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/food")
@RequiredArgsConstructor
@Tag(name = "食物", description = "食物/食材相关接口")
public class FoodController {

    private final FoodService foodService;
    
    @GetMapping
    @PublicEndpoint
    @Operation(summary = "查询所有食物/食材", description = "获取所有食物/食材的基本信息")
    public ApiResponse<List<Food>> getAllFood() {
        return ApiResponse.success(foodService.getAllFood());
    }


    @GetMapping("/{item_key}")
    @PublicEndpoint
    @Operation(summary = "根据key查询食物/食材", description = "根据key获取食物/食材的详细信息")
    public ApiResponse<Food> getFoodByKey(@PathVariable(value = "item_key") String itemKey) {
        Food Food = foodService.getFoodByKey(itemKey);
        return ApiResponse.success(Food);
    }
}
