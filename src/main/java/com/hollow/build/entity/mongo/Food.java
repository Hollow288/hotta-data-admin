package com.hollow.build.entity.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 食物实体类
 */
@Data
@Document(collection = "food") // 指定MongoDB集合名称
@Schema(name = "Food", description = "食物数据实体")
public class Food {

//    @Id
//    @Schema(description = "主键ID（MongoDB自动生成）")
//    private String id;

    @Schema(description = "食物唯一Key")
    private String foodKey;

    @Schema(description = "食物名称")
    private String foodName;

    @Schema(description = "食物描述")
    private String foodDes;

    @Schema(description = "食物图标路径")
    private String foodIcon;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "使用效果")
    private String useDescription;

    @Schema(description = "Buff效果")
    private String buffs;
}

