package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(name = "WeaponsListDto", description = "武器列表搜索")
public class WeaponsListDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "武器唯一标识", example = "fan_superpower")
    private String weaponKey;

    @Schema(description = "武器名称", example = "影织")
    private String weaponName;

    @Schema(description = "武器图标路径", example = "Resources/Icon/weapon/Icon/item_Weapon_SSR_fan_superpower01.png")
    private String weaponIcon;

    @Schema(description = "武器稀有度", example = "SSR")
    private String weaponRarity;

    @Schema(description = "武器类型", example = "强攻")
    private String weaponCategory;

    @Schema(description = "武器元素类型")
    private String weaponElementType;
}
