package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 涨跌走势图上的一个数据点：某一净值日期的单位净值与当日涨跌幅。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FundNavPointDto", description = "基金净值走势数据点")
public class FundNavPointDto implements Serializable {

    /** 净值日期 */
    @Schema(description = "净值日期")
    private LocalDate date;

    /** 单位净值 */
    @Schema(description = "单位净值")
    private BigDecimal unitNav;

    /** 当日涨跌幅(%) */
    @Schema(description = "当日涨跌幅(%)")
    private BigDecimal growthRate;
}
