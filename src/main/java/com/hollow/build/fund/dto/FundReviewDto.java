package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金点评数据包：整合「画像快照(profile)」与「从净值实时算出的风险指标」，
 * 一次性打包给 AI 做点评，AI 只需把这些指标翻译成人话，不需要自己算数。
 */
@Data
@Schema(name = "FundReviewDto", description = "基金点评数据包")
public class FundReviewDto implements Serializable {

    @Schema(description = "基金代码")
    private String fundCode;

    @Schema(description = "基金名称")
    private String fundName;

    @Schema(description = "基金类型")
    private String fundType;

    @Schema(description = "净值截至日期")
    private LocalDate asOf;

    @Schema(description = "最新单位净值")
    private BigDecimal latestNav;

    @Schema(description = "最新一日涨跌幅(%)")
    private BigDecimal latestGrowthRate;

    // ===== 阶段涨跌（来自 profile / pingzhongdata）=====
    @Schema(description = "近1月涨跌幅(%)")
    private BigDecimal return1m;
    @Schema(description = "近3月涨跌幅(%)")
    private BigDecimal return3m;
    @Schema(description = "近6月涨跌幅(%)")
    private BigDecimal return6m;
    @Schema(description = "近1年涨跌幅(%)")
    private BigDecimal return1y;

    // ===== 风险（从近1年净值实时算）=====
    @Schema(description = "近1年最大回撤(%)")
    private BigDecimal maxDrawdown;
    @Schema(description = "近1年年化波动率(%)")
    private BigDecimal volatility;
    @Schema(description = "近1年年化收益率(%)")
    private BigDecimal annualizedReturn;
    @Schema(description = "近1年夏普比率")
    private BigDecimal sharpeRatio;
    @Schema(description = "近1年卡玛比率")
    private BigDecimal calmarRatio;
    @Schema(description = "区间最高净值")
    private BigDecimal highNav;
    @Schema(description = "区间最高净值日期")
    private LocalDate highDate;
    @Schema(description = "区间最低净值")
    private BigDecimal lowNav;
    @Schema(description = "区间最低净值日期")
    private LocalDate lowDate;

    // ===== 相对表现与背景（来自 profile）=====
    @Schema(description = "同类排名百分位(0-100，越大越靠前)")
    private BigDecimal similarPercent;
    @Schema(description = "最新规模(亿元)")
    private BigDecimal fundScale;
    @Schema(description = "基金经理")
    private String managerName;
    @Schema(description = "经理星级")
    private Integer managerStar;
    @Schema(description = "经理任职年限")
    private String managerWorkTime;
    @Schema(description = "经理在管规模")
    private String managerSize;
    @Schema(description = "经理综合评分")
    private BigDecimal managerScore;
    @Schema(description = "申购费率(%)")
    private BigDecimal buyRate;

    // ===== 阶段排名（定时任务刷新入库，点评接口只读库）=====
    @Schema(description = "阶段收益/同类平均/基准/排名")
    private List<FundPeriodRankDto> periodRanks;

    // ===== 近期走势（最近若干个交易日）=====
    @Schema(description = "近期走势（最近若干个交易日）")
    private List<FundNavPointDto> recent;

    @Schema(description = "额外提示（如货币型基金、画像数据缺失等）")
    private String note;
}
