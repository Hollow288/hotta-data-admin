package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "weapons")
@Schema(description = "武器实体类")
public class Weapons implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

//    @Id
//    @Schema(description = "主键ID（MongoDB自动生成）")
//    private String id;

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

    @Schema(description = "武器元素信息")
    private WeaponElement weaponElement;

    @Schema(description = "破甲值")
    private double armorBroken;

    @Schema(description = "充能值")
    private double charging;

    @Schema(description = "武器描述")
    private String description;

    @Schema(description = "改造详情")
    private String remouldDetail;

    @Schema(description = "武器感性等级数据")
    private List<String> weaponSensualityLevelData;

    @Schema(description = "武器升级星级包")
    private List<String> weaponUpgradeStarPack;

    @Schema(description = "武器技能列表")
    private List<WeaponSkill> weaponSkill;

    // ================= 嵌套子类 =================

    @Data
    @NoArgsConstructor
    @Schema(description = "武器元素信息")
    public static class WeaponElement implements Serializable  {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "元素类型", example = "异能")
        private String weaponElementType;

        @Schema(description = "元素名称", example = "异能")
        private String weaponElementName;

        @Schema(description = "元素描述")
        private String weaponElementDesc;
    }

    @Data
    @NoArgsConstructor
    @Schema(description = "武器技能信息")
    public static class WeaponSkill implements Serializable  {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "技能类型", example = "普攻")
        private String type;

        @Schema(description = "技能名称", example = "普通攻击")
        private String name;

        @Field("des")
        @Schema(description = "技能描述")
        private String description;

        @Schema(description = "技能类别")
        private String[] tags;

        @Schema(description = "技能图标路径")
        private String icon;

        @Schema(description = "技能动态描述")
        private String dynamicDes;

        @Schema(description = "技能动态数值")
        private List<BigDecimal[]> dynamicValue;
    }
}
