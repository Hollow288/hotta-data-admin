package com.hollow.build.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(name = "ArtifactListDto", description = "源器列表搜索")
public class ArtifactListDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "源器唯一键")
    private String artifactKey;

    @Schema(description = "源器名称")
    private String artifactName;

    @Schema(description = "源器稀有度")
    private String artifactRarity;

    @Schema(description = "源器缩略图路径")
    private String artifactThumbnail;
}
