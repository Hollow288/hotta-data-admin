package com.hollow.build.trading.market;

import java.math.BigDecimal;

/** 精度字段是步长，不是小数位数；minOrderAmount 的单位为 quoteCoin。 */
public record SpotInstrument(
        String symbol,
        String status,
        String baseCoin,
        String quoteCoin,
        BigDecimal minOrderAmount,
        BigDecimal basePrecision,
        BigDecimal quotePrecision,
        BigDecimal tickSize,
        BigDecimal maxLimitOrderQty,
        BigDecimal maxMarketOrderQty,
        long asOfEpochMillis
) {}
