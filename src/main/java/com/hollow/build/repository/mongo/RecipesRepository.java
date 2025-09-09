package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Recipes;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipesRepository extends MongoRepository<Recipes, String> {
    // 按武器Key查询
    Recipes findByRecipesKey(String recipesKey);
}
