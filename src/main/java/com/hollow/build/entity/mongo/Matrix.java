package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "matrix")
@Schema(description = "意志套装信息")
public class Matrix implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //    @Id
//    @Schema(description = "主键ID（MongoDB自动生成）")
//    private String id;

    @Schema(description = "意志Key，例如 Suit_SSR59")
    private String matrixKey;

    @Schema(description = "意志名称，例如 生命赋格")
    private String matrixName;

    @Schema(description = "意志品质，例如 SSR")
    private String matrixQuality;

    @Schema(description = "意志图标路径")
    private String matrixIcon;

    @Schema(description = "意志缩略图路径")
    private String matrixThumbnail;

    @Schema(description = "意志套装效果详情（2件/3件/4件等）")
    private List<MatrixDetail> matrixDetail;

    @Schema(description = "意志列表")
    private List<MatrixSuit> matrixSuitList;

    /**
     * 内部类：意志套装效果
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "意志套装效果详情")
    public static class MatrixDetail implements Serializable  {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "效果类型，例如 '意志2件装备效果'")
        private String type;

        @Schema(description = "效果描述")
        private String desc;
    }


    /**
     * 内部类：意志列表
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "意志列表详情")
    public static class MatrixSuit implements Serializable  {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "名称")
        private String itemName;

        @Schema(description = "排序")
        private String slotIndex;

        @Schema(description = "类型")
        private String type;

        @Schema(description = "类型图标")
        private String typeIcon;

        @Schema(description = "最大等级")
        private BigDecimal matrixMaxStrengthenLevel;

        @Schema(description = "最大星级")
        private BigDecimal matrixMaxStarLevel;

        @Schema(description = "描述")
        private String description;

        @Schema(description = "升级属性加成")
        private List<List<BigDecimal>> matrixUpgradeAttribute;

        @Schema(description = "基础属性")
        private List<MatrixModifyData> matrixModifyData;

        @Schema(description = "星级加成系数")
        private List<BigDecimal> matrixCoefficientList;
    }

    @Data
    @NoArgsConstructor
    @Schema(description = "基础属性")
    public static class MatrixModifyData implements Serializable  {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "基础名称")
        private String propName;

        @Schema(description = "基础名称")
        private String propChsName;

        @Schema(description = "基础值")
        private BigDecimal propValue;

        @Schema(description = "计算方式")
        private String modifierOp;

        @Schema(description = "属性图标")
        private String attributeIcon;
    }
}

