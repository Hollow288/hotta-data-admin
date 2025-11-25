package com.hollow.build.service.impl;

import com.hollow.build.dto.RecipesDto;
import com.hollow.build.dto.RecipesListDto;
import com.hollow.build.entity.mongo.Food;
import com.hollow.build.entity.mongo.Recipes;
import com.hollow.build.repository.mongo.FoodRepository;
import com.hollow.build.repository.mongo.RecipesRepository;
import com.hollow.build.service.RecipesService;
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

@Service
@RequiredArgsConstructor
public class RecipesServiceImpl implements RecipesService {

    private final RecipesRepository recipesRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;

    private final FoodRepository foodRepository;
    
    @Override
    @Cacheable(value = "recipes_all")
    public List<Recipes> getAllRecipes() {
        return recipesRepository.findAll().stream()
                .peek(recipes -> {
                    recipes.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipes.getRecipesIcon(),"hotta"));
                }).toList();
    }

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

    @Override
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

    @Override
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
