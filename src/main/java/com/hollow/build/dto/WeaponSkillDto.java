package com.hollow.build.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
* 武器技能
* @TableName weapon_skill
*/
@Getter
@Setter
public class WeaponSkillDto implements Serializable {
    /**
    * 类型
    */
    @Schema(description = "类型")
    private String skillType;
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
