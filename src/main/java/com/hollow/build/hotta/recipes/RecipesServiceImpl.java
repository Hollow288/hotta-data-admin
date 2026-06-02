package com.hollow.build.hotta.recipes;

import com.hollow.build.hotta.recipes.RecipesDto;
import com.hollow.build.hotta.recipes.RecipesListDto;
import com.hollow.build.hotta.food.Food;
import com.hollow.build.hotta.recipes.Recipes;
import com.hollow.build.hotta.food.FoodRepository;
import com.hollow.build.hotta.recipes.RecipesRepository;
import com.hollow.build.hotta.recipes.RecipesService;
import com.hollow.build.utils.DtoMapperUtil;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 食谱服务实现类，负责查询食谱数据并组装制作方式明细。
 */
@Service
@RequiredArgsConstructor
public class RecipesServiceImpl implements RecipesService {

    private final RecipesRepository recipesRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;

    private final FoodRepository foodRepository;
    
    /**
     * 查询全部食谱信息，并补全图标访问地址。
     *
     * @return 全部食谱列表
     */
    @Override
    @Cacheable(value = "recipes_all")
    public List<Recipes> getAllRecipes() {
        return recipesRepository.findAll().stream()
                .peek(recipes -> {
                    recipes.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipes.getRecipesIcon(),"hotta"));
                }).toList();
    }

    /**
     * 根据食谱唯一键查询详情，并补全图标访问地址。
     *
     * @param itemKey 食谱唯一标识
     * @return 食谱详情，未找到时返回 null
     */
    @Override
    @Cacheable(value = "recipes", key = "#itemKey")
    public Recipes getRecipesByKey(String itemKey) {
        Recipes recipes = recipesRepository.findByRecipesKey(itemKey);

        if (recipes == null) {
            return null;
        }

        // 拼接主图标
        recipes.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipes.getRecipesIcon(),"hotta"));

        return recipes;
    }

    /**
     * 按分类筛选食谱简要信息。
     *
     * @param categories 食谱分类，可为空
     * @return 满足条件的食谱列表 DTO 集合
     */
    @Override
    @Cacheable(value = "recipes_list", key = "#categories")
    public List<RecipesListDto> getRecipesByParams(String categories) {
        Query query = new Query();

        if (categories != null && !categories.isEmpty()) {
            query.addCriteria(Criteria.where("categories").regex(".*" + categories + ".*", "i"));
        }

        query.fields()
                .include("recipesKey")
                .include("recipesName")
                .include("recipesIcon");

        List<RecipesListDto> recipesSearchList = mongoTemplate.find(query, RecipesListDto.class, "recipes");

        recipesSearchList.forEach(recipesListDto -> {
            recipesListDto.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipesListDto.getRecipesIcon(),"hotta"));
        });
        return recipesSearchList;
    }

    /**
     * 根据食谱唯一键查询制作详情，并组装食材信息与图标地址。
     *
     * @param itemKey 食谱唯一标识
     * @return 包含制作方式和食材明细的食谱 DTO
     */
    @Override
    @Cacheable(value = "recipes_howmake", key = "#itemKey")
    public RecipesDto getRecipesHowMakeByKey(String itemKey) {

        Recipes recipes = recipesRepository.findByRecipesKey(itemKey);

        List<Recipes.Ingredient> ingredients = recipes.getIngredients();

        RecipesDto recipesDto = DtoMapperUtil.map(recipes, RecipesDto.class);

        List<RecipesDto.Ingredients> ingredientsList = new ArrayList<>();

        for (Recipes.Ingredient ingredient : ingredients) {

            RecipesDto.Ingredients ingredientsInfo = new RecipesDto.Ingredients();

            String ingredientKey = ingredient.getIngredientKey();
            Food food = foodRepository.findByFoodKey(ingredientKey);

            ingredientsInfo.setIngredientIcon(minioUtil.fileUrlEncoderChance(food.getFoodIcon(),"hotta"));
            ingredientsInfo.setIngredientKey(food.getFoodKey());
            ingredientsInfo.setIngredientName(food.getFoodName());
            ingredientsInfo.setIngredientNum(ingredient.getIngredientNum());
            ingredientsList.add(ingredientsInfo);
        }

        recipesDto.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipesDto.getRecipesIcon(),"hotta"));

        recipesDto.setIngredientsList(ingredientsList);


        return recipesDto;
    }
}
