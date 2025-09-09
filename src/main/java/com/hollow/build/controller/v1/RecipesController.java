package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.entity.mongo.Recipes;
import com.hollow.build.service.RecipesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
@Tag(name = "食谱", description = "食谱相关接口")
public class RecipesController {

    private final RecipesService recipesService;
    
    @GetMapping
    @PublicEndpoint
    @Operation(summary = "查询所有食谱", description = "获取所有食谱的基本信息")
    public ApiResponse<List<Recipes>> getAllRecipes() {
        return ApiResponse.success(recipesService.getAllRecipes());
    }


    @GetMapping("/{item_key}")
    @PublicEndpoint
    @Operation(summary = "根据key查询食谱", description = "根据key获取食谱的详细信息")
    public ApiResponse<Recipes> getRecipesByKey(@PathVariable(value = "item_key") String itemKey) {
        Recipes Recipes = recipesService.getRecipesByKey(itemKey);
        return ApiResponse.success(Recipes);
    }
}
