package com.hollow.build.service.impl;

import com.hollow.build.entity.mongo.Recipes;
import com.hollow.build.repository.mongo.RecipesRepository;
import com.hollow.build.service.RecipesService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipesServiceImpl implements RecipesService {

    private final RecipesRepository recipesRepository;

    private final MinioUtil minioUtil;
    
    @Override
    @Cacheable(value = "recipes_all")
    public List<Recipes> getAllRecipes() {
        return recipesRepository.findAll().stream()
                .peek(recipes -> {
                    recipes.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipes.getRecipesIcon(),"hotta"));
                }).toList();
    }

    @Override
    @Cacheable(value = "recipes_all")
    public Recipes getRecipesByKey(String itemKey) {
        Recipes recipes = recipesRepository.findByRecipesKey(itemKey);

        if (recipes == null) {
            return null;
        }

        // 拼接主图标
        recipes.setRecipesIcon(minioUtil.fileUrlEncoderChance(recipes.getRecipesIcon(),"hotta"));

        return recipes;
    }
}
