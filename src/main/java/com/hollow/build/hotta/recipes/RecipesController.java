package com.hollow.build.hotta.recipes;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.hotta.recipes.RecipesDto;
import com.hollow.build.hotta.recipes.RecipesListDto;
import com.hollow.build.hotta.recipes.Recipes;
import com.hollow.build.hotta.recipes.RecipesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 食谱控制器，提供食谱列表、详情和制作方式查询接口。
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
@Tag(name = "食谱", description = "食谱相关接口")
public class RecipesController {

    private final RecipesService recipesService;
    
    /**
     * 查询全部食谱数据。
     *
     * @return 包含全部食谱信息的响应结果
     */
    @GetMapping
    @PublicEndpoint
    @Operation(summary = "查询所有食谱", description = "获取所有食谱的基本信息")
    public ApiResponse<List<Recipes>> getAllRecipes() {
        return ApiResponse.success(recipesService.getAllRecipes());
    }


    /**
     * 根据食谱唯一键查询详情。
     *
     * @param itemKey 食谱唯一标识
     * @return 包含食谱详情的响应结果
     */
    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询食谱", description = "根据key获取食谱的详细信息")
    public ApiResponse<Recipes> getRecipesByKey(@PathVariable(value = "item_key") String itemKey) {
        Recipes Recipes = recipesService.getRecipesByKey(itemKey);
        return ApiResponse.success(Recipes);
    }


    /**
     * 按分类条件筛选食谱列表。
     *
     * @param categories 食谱分类，可为空
     * @return 包含食谱简要信息列表的响应结果
     */
    @GetMapping("/search")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据条件查询食谱", description = "根据条件查询食谱")
    public ApiResponse<List<RecipesListDto>> getRecipesByParams(@RequestParam(required = false) String categories) {
        List<RecipesListDto> recipesListDtoList = recipesService.getRecipesByParams(categories);
        return ApiResponse.success(recipesListDtoList);
    }


    /**
     * 根据食谱唯一键查询制作方式和食材信息。
     *
     * @param itemKey 食谱唯一标识
     * @return 包含制作详情的响应结果
     */
    @GetMapping("/how-make/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询食谱/制作方式", description = "根据key获取食谱/制作方式")
    public ApiResponse<RecipesDto> getRecipesHowMakeByKey(@PathVariable(value = "item_key") String itemKey) {
        RecipesDto recipesDto = recipesService.getRecipesHowMakeByKey(itemKey);
        return ApiResponse.success(recipesDto);
    }
}
