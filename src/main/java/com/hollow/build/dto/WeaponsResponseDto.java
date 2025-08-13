package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class WeaponsResponseDto implements Serializable {
    /**
     * 武器key
     */
    @Schema(description = "武器key")
    private String itemKey;
    /**
     * 武器名称
     */
    @Schema(description = "武器名称")
    private String itemName;
    /**
     * 武器稀有度
     */
    @Schema(description = "武器稀有度")
    private String itemRarity;
    /**
     * 武器定位
     */
    @Schema(description = "武器定位")
    private String weaponCategory;
    /**
     * 武器属性
     */
    @Schema(description = "武器属性")
    private String weaponElementType;
    /**
     * 特质名称
     */
    @Schema(description = "特质名称")
    private String weaponElementName;
    /**
     * 特质描述
     */
    @Schema(description = "特质描述")
    private String weaponElementDesc;
    /**
     * 武器破防
     */
    @Schema(description = "武器破防")
    private BigDecimal armorBroken;
    /**
     * 武器充能
     */
    @Schema(description = "武器充能")
    private BigDecimal charging;
    /**
     * 武器缩略图地址
     */
    @Schema(description = "武器缩略图地址")
    private String itemIcon;
    /**
     * 武器描述
     */
    @Schema(description = "武器描述")
    private String description;
    /**
     * 专属效果
     */
    @Schema(description = "专属效果")
    private String remouldDetail;

    /**
     * 武器通感
     */
    @Schema(description = "武器通感")
    private List<WeaponSensualityLevelDataDto> weaponSensualityLevelDataList;

    /**
     * 武器技能
     */
    @Schema(description = "武器技能")
    private List<WeaponSkillDto> weaponSkills;

    /**
     * 武器星级
     */
    @Schema(description = "武器星级")
    private List<WeaponUpgradeStarPackDto> weaponUpgradeStarPack;
}
