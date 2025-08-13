package com.hollow.build.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 武器星级
* @TableName weapon_upgrade_star_pack
*/
@Getter
@Setter
public class WeaponUpgradeStarPackDto implements Serializable {
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
