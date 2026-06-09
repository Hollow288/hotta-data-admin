package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金阶段收益、同类平均、基准与排名。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FundPeriodRankDto", description = "基金阶段收益与排名")
public class FundPeriodRankDto implements Serializable {

    @Schema(description = "阶段编码")
    private String periodCode;

    @Schema(description = "阶段名称")
    private String periodName;

    @Schema(description = "基金阶段收益率(%)")
    private BigDecimal fundReturn;

    @Schema(description = "同类平均收益率(%)")
    private BigDecimal similarAverageReturn;

    @Schema(description = "基准收益率(%)，当前为沪深300")
    private BigDecimal benchmarkReturn;

    @Schema(description = "同类排名")
    private Integer rankNo;

    @Schema(description = "同类排名总数")
    private Integer rankTotal;

    @Schema(description = "数据截至日期")
    private LocalDate asOf;
}
