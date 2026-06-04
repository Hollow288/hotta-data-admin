package com.hollow.build.hotta.recipes;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 食谱（Recipes）MongoDB 数据访问接口。
 */
@Repository
public interface RecipesRepository extends MongoRepository<Recipes, String> {

    /**
     * 根据食谱唯一标识键查询食谱信息。
     *
     * @param recipesKey 食谱唯一标识键
     * @return 匹配的食谱对象，未找到时返回 null
     */
    Recipes findByRecipesKey(String recipesKey);
}
