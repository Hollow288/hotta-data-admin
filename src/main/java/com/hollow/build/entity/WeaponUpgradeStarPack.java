package com.hollow.build.entity;


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
public class WeaponUpgradeStarPack implements Serializable {

    /**
    * 星级id
    */
    @Schema(description = "星级id")
    private Integer upgradeStarId;
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
