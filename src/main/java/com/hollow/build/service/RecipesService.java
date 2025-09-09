package com.hollow.build.service;

import com.hollow.build.entity.mongo.Recipes;

import java.util.List;

public interface RecipesService {
    List<Recipes> getAllRecipes();

    Recipes getRecipesByKey(String itemKey);
}
