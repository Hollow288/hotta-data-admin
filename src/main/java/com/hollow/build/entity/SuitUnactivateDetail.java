package com.hollow.build.entity;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 意志套装效果
* @TableName suit_unactivate_detail
*/
@Getter
@Setter
public class SuitUnactivateDetail implements Serializable {

    /**
    * 意志套装id
    */
    @Schema(description = "意志套装id")
    private Integer detailId;
    /**
    * 意志id
    */
    @Schema(description = "意志id")
    private Integer matrixId;
    /**
    * 套装名称
    */
    @Schema(description = "套装名称")
    private String itemName;
    /**
    * 套装描述
    */
    @Schema(description = "套装描述")
    private String itemDescribe;

}
