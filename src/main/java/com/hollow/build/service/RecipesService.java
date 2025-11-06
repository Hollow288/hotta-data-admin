package com.hollow.build.service;

import com.hollow.build.dto.RecipesDto;
import com.hollow.build.dto.RecipesListDto;
import com.hollow.build.entity.mongo.Recipes;

import java.util.List;

public interface RecipesService {
    List<Recipes> getAllRecipes();

    Recipes getRecipesByKey(String itemKey);

    List<RecipesListDto> getRecipesByParams(String categories);

    RecipesDto getRecipesHowMakeByKey(String itemKey);
}
