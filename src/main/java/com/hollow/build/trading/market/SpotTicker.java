package com.hollow.build.trading.market;

import java.math.BigDecimal;

/** change24hPercent 已从 Bybit 的比值换算为百分数，例如 1.25 表示上涨 1.25%。 */
public record SpotTicker(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal change24hPercent,
        BigDecimal volume24h,
        BigDecimal turnover24h,
        long asOfEpochMillis
) {}
