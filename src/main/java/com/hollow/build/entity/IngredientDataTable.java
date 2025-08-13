package com.hollow.build.entity;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
* 食材信息
* @TableName ingredient_data_table
*/
@Getter
@Setter
public class IngredientDataTable implements Serializable {

    /**
    * 食材id
    */
    @Schema(description = "食材id")
    private Integer ingredientId;
    /**
    * 食材key
    */
    @Schema(description = "食材key")
    private String ingredientKey;
    /**
    * 食材名称
    */
    @Schema(description = "食材名称")
    private String ingredientName;
    /**
    * 食材描述
    */
    @Schema(description = "食材描述")
    private String ingredientDes;
    /**
    * 食材缩略图地址
    */
    @Schema(description = "食材缩略图地址")
    private String ingredientIcon;
    /**
    * 食材来源
    */
    @Schema(description = "食材来源")
    private String ingredientSource;

}
