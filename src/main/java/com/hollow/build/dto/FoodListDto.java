package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(name = "FoodListDto", description = "食物列表搜索")
public class FoodListDto implements Serializable {

    @Schema(description = "食物唯一Key")
    private String foodKey;

    @Schema(description = "食物名称")
    private String foodName;

    @Schema(description = "食物图标路径")
    private String foodIcon;

}
