package com.hollow.build.fund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金阶段收益与同类/基准排名，对应 {@code fund_period_rank} 表。
 */
@Data
public class FundPeriodRank implements Serializable {

    /** 主键ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 基金代码 */
    @Schema(description = "基金代码", example = "270042")
    private String fundCode;

    /** 阶段编码，如 Z/Y/3Y/6Y/1N/2N/3N/5N/JN/LN */
    @Schema(description = "阶段编码")
    private String periodCode;

    /** 阶段名称，如 近1周/近1月/近3月/近6月/近1年 */
    @Schema(description = "阶段名称")
    private String periodName;

    /** 基金阶段收益率(%) */
    @Schema(description = "基金阶段收益率(%)")
    private BigDecimal fundReturn;

    /** 同类平均收益率(%) */
    @Schema(description = "同类平均收益率(%)")
    private BigDecimal similarAverageReturn;

    /** 基准收益率(%)，当前接口返回沪深300 */
    @Schema(description = "基准收益率(%)")
    private BigDecimal benchmarkReturn;

    /** 同类排名 */
    @Schema(description = "同类排名")
    private Integer rankNo;

    /** 同类排名总数 */
    @Schema(description = "同类排名总数")
    private Integer rankTotal;

    /** 数据截至日期 */
    @Schema(description = "数据截至日期")
    private LocalDate asOf;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
