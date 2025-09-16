package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * MongoDB Fashion 实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "fashion") // 替换成你实际的集合名
@Schema(name = "Fashion", description = "时装实体类")
public class Fashion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

//    @Id
//    @Schema(description = "主键ID（MongoDB自动生成）")
//    private String id;

    @Schema(description = "时装唯一键")
    private String fashionKey;

    @Schema(description = "时装名称")
    private String fashionName;

    @Schema(description = "时装描述")
    private String description;

    @Schema(description = "获取来源")
    private String source;

    @Schema(description = "时装图标路径列表")
    private List<String> fashionIcons;
}

