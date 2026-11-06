package com.hollow.build.trading.paper;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.hollow.build.trading.paper.PaperModels.*;

/** 账本事实统计；基准从首次同时具备完整权益和 BTC 报价的采样点开始，不补造过去价格。 */
@Service
public class PaperPerformanceService {
    private final JdbcTemplate jdbc;
    public PaperPerformanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Performance(int equitySampleCount, Long observationStartEpochMillis, Long observationEndEpochMillis,
                              BigDecimal sampledMaxDrawdownPercent, Long benchmarkStartEpochMillis, Long benchmarkEndEpochMillis,
                              BigDecimal strategyReturnPercent, BigDecimal btcBuyHoldReturnPercent,
                              BigDecimal excessReturnPercentagePoints, int sellFillCount, int winningSellFillCount,
                              BigDecimal sellFillWinRatePercent, BigDecimal averageWinLossRatio, BigDecimal profitFactor,
                              String methodology) {}
    private record Sample(long time, BigDecimal equity, BigDecimal btc) {}

    public Performance calculate(String accountId) {
        var samples = jdbc.query("""
                SELECT as_of_epoch_ms,equity_usdt,btc_price_usdt FROM trading_paper_equity_sample
                WHERE account_id=? ORDER BY sample_sequence,sample_id
                """, (rs, row) -> new Sample(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), accountId);
        BigDecimal peak = null, drawdown = samples.isEmpty() ? null : BigDecimal.ZERO;
        Sample first = null, last = null;
        for (Sample sample : samples) {
            peak = peak == null ? sample.equity() : peak.max(sample.equity());
            if (peak.signum() > 0) drawdown = drawdown.max(percent(peak.subtract(sample.equity()), peak));
            if (sample.btc() != null && sample.btc().signum() > 0 && (first != null || sample.equity().signum() > 0)) {
                if (first == null) first = sample;
                last = sample;
            }
        }
        BigDecimal strategy = first == null ? null : percent(last.equity().subtract(first.equity()), first.equity());
        BigDecimal benchmark = first == null ? null : percent(last.btc().subtract(first.btc()), first.btc());
        int sells = 0, wins = 0, losses = 0;
        BigDecimal gains = BigDecimal.ZERO, costs = BigDecimal.ZERO;
        var fills = jdbc.queryForList("SELECT fill_json FROM trading_paper_run WHERE account_id=? AND status='FILLED'",
                String.class, accountId);
        for (String json : fills) {
            Fill fill = JSON.parseObject(json, Fill.class);
            if (fill == null || fill.side() != TradeDecision.Action.SELL) continue;
            sells++;
            if (fill.realizedPnlUsdt().signum() > 0) { wins++; gains = gains.add(fill.realizedPnlUsdt()); }
            if (fill.realizedPnlUsdt().signum() < 0) { losses++; costs = costs.subtract(fill.realizedPnlUsdt()); }
        }
        BigDecimal ratio = wins == 0 || losses == 0 ? null
                : gains.multiply(BigDecimal.valueOf(losses)).divide(costs.multiply(BigDecimal.valueOf(wins)), 8, RoundingMode.HALF_EVEN);
        return new Performance(samples.size(), samples.isEmpty() ? null : samples.getFirst().time(),
                samples.isEmpty() ? null : samples.getLast().time(), drawdown,
                first == null ? null : first.time(), last == null ? null : last.time(), strategy, benchmark,
                strategy == null ? null : strategy.subtract(benchmark), sells, wins,
                sells == 0 ? null : percent(BigDecimal.valueOf(wins), BigDecimal.valueOf(sells)), ratio,
                costs.signum() == 0 ? null : gains.divide(costs, 8, RoundingMode.HALF_EVEN),
                "回撤仅覆盖已保存完整估值采样点，可能漏掉盘中回撤；BTC为同期价格Buy&Hold基准，未扣基准买卖费用/滑点；"
                        + "策略与BTC收益共用首末可比较采样点，缺行情时终点可能早于最新权益采样；"
                        + "胜率及盈亏比按SELL成交笔统计（含分批减仓，平局计入分母），不是完整持仓周期胜率；无亏损样本时比率为null。");
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 8, RoundingMode.HALF_EVEN);
    }
}
