package com.hollow.build.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;



/**
* 食谱
* @TableName cook_recipes_data_table
*/
@Getter
@Setter
public class CookRecipesDataTable implements Serializable {

    /**
    * 食谱id
    */
    @Schema(description = "食谱id")
    private Integer recipesId;
    /**
    * 食谱key
    */
    @Schema(description = "食谱key")
    private String recipesKey;
    /**
    * 食谱名称
    */
    @Schema(description = "食谱名称")
    private String recipesName;
    /**
    * 描述
    */
    @Schema(description = "描述")
    private String recipesDes;
    /**
    * 图标
    */
    @Schema(description = "图标")
    private String recipesIcon;
    /**
    * 分类
    */
    @Schema(description = "分类")
    private String categories;
    /**
    * 效果描述
    */
    @Schema(description = "效果描述")
    private String useDescription;
    /**
    * buff
    */
    @Schema(description = "buff")
    private String buffs;


}
