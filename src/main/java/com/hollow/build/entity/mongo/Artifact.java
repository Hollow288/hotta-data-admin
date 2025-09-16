package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * MongoDB Artifact 实体类
 */
@Data
@NoArgsConstructor
@Document(collection = "artifact") // 这里的 "artifact" 替换为你的集合名
@Schema(name = "Artifact", description = "源器实体类")
public class Artifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

//    @Id
//    @Schema(description = "主键ID（MongoDB自动生成）")
//    private String id;

    @Schema(description = "源器唯一键")
    private String artifactKey;

    @Schema(description = "源器名称")
    private String artifactName;

    @Schema(description = "使用说明")
    private String useDescription;

    @Schema(description = "源器稀有度")
    private String artifactRarity;

    @Schema(description = "源器图标路径")
    private String artifactIcon;

    @Schema(description = "源器星级描述")
    private List<String> artifactDetail;
}
