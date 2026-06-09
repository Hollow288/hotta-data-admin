package com.hollow.build.fund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金每日真实净值实体，对应 {@code fund_nav} 表。
 * <p>只存基金公司公布的官方真实净值，不存盘中估值。</p>
 */
@Data
public class FundNav implements Serializable {

    /** 主键ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 基金代码 */
    @Schema(description = "基金代码", example = "270042")
    private String fundCode;

    /** 净值日期 */
    @Schema(description = "净值日期")
    private LocalDate navDate;

    /** 单位净值 */
    @Schema(description = "单位净值")
    private BigDecimal unitNav;

    /** 累计净值 */
    @Schema(description = "累计净值")
    private BigDecimal accNav;

    /** 日增长率(%) */
    @Schema(description = "日增长率(%)")
    private BigDecimal growthRate;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
