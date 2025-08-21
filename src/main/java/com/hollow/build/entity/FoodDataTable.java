package com.hollow.build.entity;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;


/**
* 食物信息(非制作)
* @TableName food_data_table
*/
public class FoodDataTable implements Serializable {

    /**
    * 食材id
    */
    @Schema(description ="食材id")
    private Integer foodId;
    /**
    * 食材key
    */
    @Schema(description ="食材key")
    private String foodKey;
    /**
    * 食材名称
    */
    @Schema(description ="食材名称")
    private String foodName;
    /**
    * 食材描述
    */
    @Schema(description ="食材描述")
    private String foodDes;
    /**
    * 食材缩略图地址
    */
    @Schema(description ="食材缩略图地址")
    private String foodIcon;
    /**
    * 食材来源
    */
    @Schema(description ="食材来源")
    private String foodSource;
    /**
    * 效果描述
    */
    @Schema(description ="效果描述")
    private String useDescription;
    /**
    * buff
    */
    @Schema(description ="buff")
    private String buffs;

}
