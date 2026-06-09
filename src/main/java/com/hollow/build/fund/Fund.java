package com.hollow.build.fund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金信息实体，对应 {@code fund} 表，表示一只被监控的基金。
 */
@Data
public class Fund implements Serializable {

    /** 主键ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 基金代码（6位） */
    @Schema(description = "基金代码（6位）", example = "270042")
    private String fundCode;

    /** 基金名称 */
    @Schema(description = "基金名称")
    private String fundName;

    /** 基金类型（QDII/指数型-股票/债券型/货币型…） */
    @Schema(description = "基金类型（QDII/指数型-股票/债券型/货币型…）")
    private String fundType;

    /** 是否监控 1是 0否 */
    @Schema(description = "是否监控 1是 0否")
    private Integer enabled;

    /** 已入库的最新净值日期（增量拉取用） */
    @Schema(description = "已入库的最新净值日期")
    private LocalDate latestNavDate;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
