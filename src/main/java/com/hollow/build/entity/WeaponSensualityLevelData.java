package com.hollow.build.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 武器通感
* @TableName weapon_sensuality_level_data
*/
@Getter
@Setter
public class WeaponSensualityLevelData implements Serializable {

    /**
    * 通感id
    */
    @Schema(description = "通感id")
    private Integer sensualityId;
    /**
    * 武器id
    */
    @Schema(description = "武器id")
    private Integer weaponsId;
    /**
    * 词条名称
    */
    @Schema(description = "词条名称")
    private String itemName;
    /**
    * 词条描述
    */
    @Schema(description = "词条描述")
    private String itemDescribe;

}
