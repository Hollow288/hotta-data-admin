package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(name = "MatrixListDto", description = "意志列表搜索")
public class MatrixListDto implements Serializable {

    @Schema(description = "意志Key，例如 Suit_SSR59")
    private String matrixKey;

    @Schema(description = "意志名称，例如 生命赋格")
    private String matrixName;

    @Schema(description = "意志图标路径")
    private String matrixIcon;

}
