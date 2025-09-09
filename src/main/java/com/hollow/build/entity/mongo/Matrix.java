package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
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

    @Schema(description = "意志套装效果详情（2件/3件/4件等）")
    private List<MatrixDetail> matrixDetail;

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
}

