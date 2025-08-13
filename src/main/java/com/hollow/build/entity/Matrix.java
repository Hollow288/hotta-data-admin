package com.hollow.build.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 意志基本信息
* @TableName matrix
*/
@Getter
@Setter
public class Matrix implements Serializable {

    /**
    * 意志id
    */
    @Schema(description = "意志id")
    private Integer matrixId;
    /**
    * 意志key
    */
    @Schema(description = "意志key")
    private String matrixKey;
    /**
    * 意志品质
    */
    @Schema(description = "意志品质")
    private String matrixSuitQuality;
    /**
    * 意志名称
    */
    @Schema(description = "意志名称")
    private String suitName;
    /**
    * 意志稀有度
    */
    @Schema(description = "意志稀有度")
    private String suitIcon;

}
