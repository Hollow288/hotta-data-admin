package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金涨跌查询返回体：基金基本信息 + 区间统计 + 净值走势序列（供机器人画涨跌图）。
 */
@Data
@Schema(name = "FundTrendDto", description = "基金涨跌走势")
public class FundTrendDto implements Serializable {

    /** 基金代码 */
    @Schema(description = "基金代码")
    private String fundCode;

    /** 基金名称 */
    @Schema(description = "基金名称")
    private String fundName;

    /** 基金类型 */
    @Schema(description = "基金类型")
    private String fundType;

    /** 查询的天数区间 */
    @Schema(description = "查询的天数区间")
    private Integer days;

    /** 最新净值日期（数据截至哪天） */
    @Schema(description = "最新净值日期（数据截至哪天）")
    private LocalDate latestNavDate;

    /** 最新单位净值 */
    @Schema(description = "最新单位净值")
    private BigDecimal latestNav;

    /** 最新一日涨跌幅(%) */
    @Schema(description = "最新一日涨跌幅(%)")
    private BigDecimal latestGrowthRate;

    /** 区间涨跌幅(%)：(区间最新净值 - 区间最早净值) / 区间最早净值 */
    @Schema(description = "区间涨跌幅(%)")
    private BigDecimal periodGrowthRate;

    /** 净值走势序列（按日期升序） */
    @Schema(description = "净值走势序列（按日期升序）")
    private List<FundNavPointDto> points;

    /** 额外提示（如货币型基金无涨跌） */
    @Schema(description = "额外提示")
    private String note;
}
