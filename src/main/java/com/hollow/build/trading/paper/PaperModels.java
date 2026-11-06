package com.hollow.build.trading.paper;

import com.hollow.build.trading.config.TradingProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 模拟交易的数据契约；金额均为 USDT，数量单位为 symbol 的基础币。 */
public final class PaperModels {
    private PaperModels() {}

    public enum RunStatus { RUNNING, FILLED, HOLD, REJECTED, ERROR }

    public record Rules(List<String> allowedSymbols, BigDecimal feeRate, BigDecimal slippageBps,
                        int maxTradesPerDay, long maxMarketAgeMillis, long runTimeoutMillis) {
        public static Rules from(TradingProperties.Paper properties) {
            Rules rules = new Rules(List.copyOf(properties.getAllowedSymbols()), properties.getFeeRate(),
                    properties.getSlippageBps(), properties.getMaxTradesPerDay(),
                    properties.getMaxMarketAge().toMillis(), properties.getRunTimeout().toMillis());
            rules.validate();
            return rules;
        }

        public void validate() {
            if (allowedSymbols == null || allowedSymbols.isEmpty() || allowedSymbols.size() > 5
                    || allowedSymbols.stream().anyMatch(s -> s == null || !s.matches("[A-Z0-9]{3,30}") || !s.endsWith("USDT"))
                    || allowedSymbols.stream().distinct().count() != allowedSymbols.size()
                    || feeRate == null || feeRate.signum() < 0 || feeRate.compareTo(new BigDecimal("0.1")) > 0
                    || feeRate.scale() > 8 || slippageBps == null || slippageBps.signum() < 0
                    || slippageBps.compareTo(new BigDecimal("500")) > 0 || slippageBps.scale() > 4
                    || maxTradesPerDay < 1 || maxTradesPerDay > 100
                    || maxMarketAgeMillis < 1000 || maxMarketAgeMillis > 300_000
                    || runTimeoutMillis < 60_000 || runTimeoutMillis > 3_600_000) {
                throw new IllegalArgumentException("模拟规则无效，请检查币种白名单、费率、滑点、次数和超时配置");
            }
        }
    }

    public record PositionPlan(Long openedAtEpochMillis, String entryThesis,
                               BigDecimal stopLossPrice, BigDecimal takeProfitPrice) {}

    public record Position(String symbol, BigDecimal quantity, BigDecimal costBasisUsdt, PositionPlan plan) {
        public Position(String symbol, BigDecimal quantity, BigDecimal costBasisUsdt) {
            this(symbol, quantity, costBasisUsdt, null);
        }
    }

    /** 均价含买入手续费；浮盈亏未扣未来卖出费用。历史缺失的建仓时间保留 null。 */
    public record PositionValuation(String symbol, BigDecimal averageCostUsdt, BigDecimal markPriceUsdt,
                                    BigDecimal unrealizedPnlUsdt, BigDecimal unrealizedPnlPercent,
                                    BigDecimal holdingDays) {}

    public record Account(String accountId, long ownerId, String name, BigDecimal initialCashUsdt,
                          BigDecimal cashUsdt, BigDecimal feesUsdt, BigDecimal realizedPnlUsdt,
                          long version, String activeRunId, Rules rules, List<Position> positions,
                          long createdAtEpochMillis) {}

    public record Snapshot(BigDecimal initialCashUsdt, BigDecimal cashUsdt, BigDecimal feesUsdt,
                           BigDecimal realizedPnlUsdt, List<Position> positions,
                           Map<String, BigDecimal> markPricesUsdt, BigDecimal equityUsdt,
                           BigDecimal totalPnlUsdt, boolean valuationComplete, long asOfEpochMillis,
                           List<PositionValuation> positionValuations, BigDecimal benchmarkBtcPriceUsdt) {
        public Snapshot(BigDecimal initialCashUsdt, BigDecimal cashUsdt, BigDecimal feesUsdt,
                        BigDecimal realizedPnlUsdt, List<Position> positions, Map<String, BigDecimal> markPricesUsdt,
                        BigDecimal equityUsdt, BigDecimal totalPnlUsdt, boolean valuationComplete, long asOfEpochMillis) {
            this(initialCashUsdt, cashUsdt, feesUsdt, realizedPnlUsdt, positions, markPricesUsdt,
                    equityUsdt, totalPnlUsdt, valuationComplete, asOfEpochMillis, List.of(), null);
        }
    }

    public record Fill(String symbol, TradeDecision.Action side, BigDecimal baseQuantity,
                       BigDecimal priceUsdt, BigDecimal grossUsdt, BigDecimal feeUsdt,
                       BigDecimal cashDeltaUsdt, BigDecimal realizedPnlUsdt) {}

    public record Run(String runId, String accountId, String idempotencyKey, RunStatus status,
                      Rules rules, TradeDecision decision, Fill fill, Snapshot before, Snapshot after,
                      String error, List<String> debugTrace, long startedAtEpochMillis, Long completedAtEpochMillis) {}

    public record RunSummary(String runId, RunStatus status, TradeDecision decision, Fill fill, String error,
                             Long completedAtEpochMillis) {}

    public record EquitySample(String sampleId, long sequence, long asOfEpochMillis,
                               BigDecimal equityUsdt, BigDecimal btcPriceUsdt) {}

    public record DecisionContext(Snapshot portfolio, Rules rules, List<RunSummary> recentRuns,
                                  String instruction) {}

    public record Reservation(Run run, Account account, boolean created) {}

    public record Execution(BigDecimal cashUsdt, BigDecimal feesUsdt, BigDecimal realizedPnlUsdt,
                            List<Position> positions, Fill fill) {}

    public record Completion(RunStatus status, TradeDecision decision, Execution execution,
                             Snapshot after, String error, List<String> debugTrace) {}
}
