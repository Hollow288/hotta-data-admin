package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "食谱/菜谱实体")
public class RecipesDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "菜谱Key")
    private String recipesKey;

    @Schema(description = "菜谱名称")
    private String recipesName;

    @Schema(description = "菜谱描述")
    private String recipesDes;

    @Schema(description = "菜谱图标路径")
    private String recipesIcon;

    @Schema(description = "菜谱类别")
    private String categories;

    @Schema(description = "使用效果描述")
    private String useDescription;

    @Schema(description = "Buff效果描述")
    private String buffs;

    @Schema(description = "材料列表")
    private List<RecipesDto.Ingredients> ingredientsList;

    @Data
    @NoArgsConstructor
    @Schema(description = "材料信息")
    public static class Ingredients implements Serializable  {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "材料名称")
        private String ingredientKey;

        @Schema(description = "材料数量")
        private Integer ingredientNum;

        @Schema(description = "材料名称")
        private String ingredientName;

        @Schema(description = "材料图标")
        private String ingredientIcon;
    }
}
