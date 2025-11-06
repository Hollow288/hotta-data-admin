package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(name = "RecipesListDto", description = "食谱列表搜索")
public class RecipesListDto implements Serializable {

    @Schema(description = "食谱唯一Key")
    private String recipesKey;

    @Schema(description = "食谱名称")
    private String recipesName;

    @Schema(description = "食谱图标路径")
    private String recipesIcon;

}
