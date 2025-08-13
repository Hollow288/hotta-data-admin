package com.hollow.build.entity;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 食谱食材对照
* @TableName recipes_ingredients
*/
@Getter
@Setter
public class RecipesIngredients implements Serializable {

    /**
    * 对照id
    */
    @Schema(description = "对照id")
    private Integer recipesIngredientsId;
    /**
    * 食材id
    */
    @Schema(description = "食材id")
    private Integer ingredientId;
    /**
    * 食谱id
    */
    @Schema(description = "食谱id")
    private Integer recipesId;
    /**
    * 数量
    */
    @Schema(description = "数量")
    private Integer amount;

}
