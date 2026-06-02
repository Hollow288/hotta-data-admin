package com.hollow.build.hotta.recipes;

import com.hollow.build.hotta.recipes.RecipesDto;
import com.hollow.build.hotta.recipes.RecipesListDto;
import com.hollow.build.hotta.recipes.Recipes;

import java.util.List;

/**
 * 食谱服务接口，提供食谱的查询及制作方式查询功能
 */
public interface RecipesService {

    /**
     * 获取所有食谱列表
     *
     * @return 所有食谱的列表
     */
    List<Recipes> getAllRecipes();

    /**
     * 根据食谱唯一标识获取食谱详情
     *
     * @param itemKey 食谱的唯一标识键
     * @return 对应的食谱对象
     */
    Recipes getRecipesByKey(String itemKey);

    /**
     * 根据分类查询食谱列表
     *
     * @param categories 食谱分类
     * @return 符合条件的食谱列表DTO
     */
    List<RecipesListDto> getRecipesByParams(String categories);

    /**
     * 根据食谱唯一标识查询食谱的制作方式
     *
     * @param itemKey 食谱的唯一标识键
     * @return 包含制作方式的食谱DTO
     */
    RecipesDto getRecipesHowMakeByKey(String itemKey);
}
