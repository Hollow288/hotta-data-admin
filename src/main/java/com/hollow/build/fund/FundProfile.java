package com.hollow.build.fund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金画像快照实体，对应 {@code fund_profile} 表。
 * <p>数据来自天天基金 pingzhongdata 接口，每只基金一行、每天覆盖更新，供 AI 点评使用。</p>
 */
@Data
public class FundProfile implements Serializable {

    /** 基金代码（与 fund 一对一） */
    @Schema(description = "基金代码", example = "270042")
    private String fundCode;

    /** 近1月涨跌幅(%) */
    @Schema(description = "近1月涨跌幅(%)")
    private BigDecimal return1m;

    /** 近3月涨跌幅(%) */
    @Schema(description = "近3月涨跌幅(%)")
    private BigDecimal return3m;

    /** 近6月涨跌幅(%) */
    @Schema(description = "近6月涨跌幅(%)")
    private BigDecimal return6m;

    /** 近1年涨跌幅(%) */
    @Schema(description = "近1年涨跌幅(%)")
    private BigDecimal return1y;

    /** 同类排名百分位(0-100，越大越靠前) */
    @Schema(description = "同类排名百分位(0-100，越大越靠前)")
    private BigDecimal similarPercent;

    /** 最新规模(亿元) */
    @Schema(description = "最新规模(亿元)")
    private BigDecimal fundScale;

    /** 规模对应季度末日期 */
    @Schema(description = "规模对应季度末日期")
    private LocalDate scaleDate;

    /** 现任基金经理 */
    @Schema(description = "现任基金经理")
    private String managerName;

    /** 经理星级 */
    @Schema(description = "经理星级")
    private Integer managerStar;

    /** 任职年限(原样，如 12年又72天) */
    @Schema(description = "任职年限")
    private String managerWorkTime;

    /** 在管规模(原样) */
    @Schema(description = "在管规模")
    private String managerSize;

    /** 经理综合评分 */
    @Schema(description = "经理综合评分")
    private BigDecimal managerScore;

    /** 申购费率(%) */
    @Schema(description = "申购费率(%)")
    private BigDecimal buyRate;

    /** 起购金额(元) */
    @Schema(description = "起购金额(元)")
    private BigDecimal minBuy;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
