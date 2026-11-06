package com.hollow.build.trading.market;

import java.math.BigDecimal;
import java.util.List;

/** candles 按开始时间升序排列；closed=false 的蜡烛仍在形成，close 是当前最新成交价。 */
public record SpotKlines(String symbol, String interval, List<Candle> candles, long asOfEpochMillis) {

    public record Candle(
            long startTimeEpochMillis,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            BigDecimal turnover,
            boolean closed
    ) {}
}
