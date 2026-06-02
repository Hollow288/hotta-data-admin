package com.hollow.build.hotta.food;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.hotta.food.FoodListDto;
import com.hollow.build.hotta.food.Food;
import com.hollow.build.hotta.food.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 食物控制器，提供食物列表、详情和检索接口。
 */
@RestController
@RequestMapping("/api/v1/food")
@RequiredArgsConstructor
@Tag(name = "食物", description = "食物/食材相关接口")
public class FoodController {

    private final FoodService foodService;
    
    /**
     * 查询全部食物数据。
     *
     * @return 包含全部食物信息的响应结果
     */
    @GetMapping
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询所有食物/食材", description = "获取所有食物/食材的基本信息")
    public ApiResponse<List<Food>> getAllFood() {
        return ApiResponse.success(foodService.getAllFood());
    }


    /**
     * 根据食物唯一键查询详情。
     *
     * @param itemKey 食物唯一标识
     * @return 包含食物详情的响应结果
     */
    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询食物/食材", description = "根据key获取食物/食材的详细信息")
    public ApiResponse<Food> getFoodByKey(@PathVariable(value = "item_key") String itemKey) {
        Food Food = foodService.getFoodByKey(itemKey);
        return ApiResponse.success(Food);
    }


    /**
     * 查询用于列表展示的食物简要信息。
     *
     * @return 包含食物列表 DTO 的响应结果
     */
    @GetMapping("/search")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据条件查询食物", description = "根据条件查询食物")
    public ApiResponse<List<FoodListDto>> getFoodByParams() {
        List<FoodListDto> foodListDtoList = foodService.getFoodByParams();
        return ApiResponse.success(foodListDtoList);
    }
}
