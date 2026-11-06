package com.hollow.build.trading.paper;

import com.hollow.build.trading.market.SpotInstrument;
import com.hollow.build.trading.market.SpotTicker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.hollow.build.trading.paper.PaperModels.*;
import static com.hollow.build.trading.paper.TradeDecision.Action.*;

/** 确定性的硬规则。所有模拟成交也必须先通过这里，提示词不能改变这些约束。 */
@Service
public class RiskControlService {

    public record ApprovedOrder(String symbol, TradeDecision.Action side, BigDecimal quantity,
                                BigDecimal price, BigDecimal gross, BigDecimal fee) {}

    public static class RiskViolation extends RuntimeException {
        public RiskViolation(String message) { super(message); }
    }

    public ApprovedOrder validate(TradeDecision decision, Account account, SpotTicker ticker,
                                  SpotInstrument instrument, int tradesToday, long now) {
        validateIntent(decision, account);
        if (decision.action() == HOLD) return null;
        // 保留旧 rules_json/config 字段名，语义改为每日买入次数，减仓不占额度。
        if (decision.action() == BUY) {
            require(tradesToday < account.rules().maxTradesPerDay(), "已达到今日模拟买入次数上限（北京时间）");
        }
        require(ticker != null && instrument != null, "缺少执行所需的行情或交易规则");
        require(decision.symbol().equals(ticker.symbol()) && decision.symbol().equals(instrument.symbol()), "行情交易对与决策不一致");
        require("USDT".equals(instrument.quoteCoin()) && "Trading".equals(instrument.status()), "仅支持正常交易的 USDT 现货交易对");
        fresh(ticker.asOfEpochMillis(), now, account.rules().maxMarketAgeMillis());
        fresh(instrument.asOfEpochMillis(), now, account.rules().maxMarketAgeMillis());
        positive(instrument.basePrecision(), "数量步长");
        positive(instrument.quotePrecision(), "金额步长");
        positive(instrument.tickSize(), "价格步长");
        positive(instrument.minOrderAmount(), "最低金额");
        positive(instrument.maxMarketOrderQty(), "市价单最大数量");
        positive(ticker.bidPrice(), "买一价");
        positive(ticker.askPrice(), "卖一价");
        require(ticker.bidPrice().compareTo(ticker.askPrice()) <= 0, "买卖报价异常");
        BigDecimal slippage = account.rules().slippageBps().movePointLeft(4);
        boolean buy = decision.action() == BUY;
        BigDecimal price = step((buy ? ticker.askPrice() : ticker.bidPrice())
                        .multiply(buy ? BigDecimal.ONE.add(slippage) : BigDecimal.ONE.subtract(slippage)),
                instrument.tickSize(), buy ? RoundingMode.CEILING : RoundingMode.FLOOR);
        positive(price, "模拟成交价格");
        if (buy) {
            PositionPlan oldPlan = account.positions().stream().filter(p -> p.symbol().equals(decision.symbol()))
                    .map(Position::plan).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            BigDecimal stop = decision.stopLossPrice() != null ? decision.stopLossPrice()
                    : oldPlan == null ? null : oldPlan.stopLossPrice();
            BigDecimal target = decision.takeProfitPrice() != null ? decision.takeProfitPrice()
                    : oldPlan == null ? null : oldPlan.takeProfitPrice();
            if (stop != null) require(stop.compareTo(ticker.bidPrice()) < 0 && stop.compareTo(price) < 0,
                    "止损价必须低于当前买一价和模拟买入价");
            if (target != null) require(target.compareTo(price) > 0, "止盈价必须高于模拟买入价");
        }
        BigDecimal quantity;
        if (buy) {
            quantity = buyQuantity(decision.quoteBudgetUsdt(), price, account.rules().feeRate(), instrument.basePrecision());
        } else {
            quantity = decision.baseQuantity();
            require(quantity.remainder(instrument.basePrecision()).signum() == 0, "卖出数量不符合基础币步长");
            BigDecimal holding = account.positions().stream().filter(p -> p.symbol().equals(decision.symbol()))
                    .map(Position::quantity).findFirst().orElse(BigDecimal.ZERO);
            require(quantity.compareTo(holding) <= 0, "卖出数量超过持仓，禁止做空");
        }
        BigDecimal gross = step(quantity.multiply(price), instrument.quotePrecision(),
                buy ? RoundingMode.CEILING : RoundingMode.FLOOR);
        BigDecimal fee = step(gross.multiply(account.rules().feeRate()), instrument.quotePrecision(), RoundingMode.CEILING);
        if (buy && gross.add(fee).compareTo(decision.quoteBudgetUsdt()) > 0) {
            // 报价金额和手续费分别向上舍入，最多预留两个报价步长后重新计算，避免越过总预算。
            BigDecimal reserve = instrument.quotePrecision().multiply(BigDecimal.valueOf(2));
            quantity = buyQuantity(decision.quoteBudgetUsdt().subtract(reserve), price,
                    account.rules().feeRate(), instrument.basePrecision());
            gross = step(quantity.multiply(price), instrument.quotePrecision(), RoundingMode.CEILING);
            fee = step(gross.multiply(account.rules().feeRate()), instrument.quotePrecision(), RoundingMode.CEILING);
        }
        require(quantity.signum() > 0, "预算不足以购买一个数量步长");
        require(quantity.compareTo(instrument.maxMarketOrderQty()) <= 0, "订单超过交易对市价单最大数量");
        require(gross.compareTo(instrument.minOrderAmount()) >= 0,
                "订单金额低于最低 " + instrument.minOrderAmount().toPlainString() + " USDT（手续费不计入最低金额）");
        if (buy) {
            require(gross.add(fee).compareTo(decision.quoteBudgetUsdt()) <= 0, "含手续费的成交金额超过买入预算");
        } else {
            require(gross.compareTo(fee) > 0, "卖出收入不足以支付手续费");
        }
        return new ApprovedOrder(decision.symbol(), decision.action(), quantity, price, gross, fee);
    }

    public void validateIntent(TradeDecision decision, Account account) {
        try {
            decision.validateShape();
            account.rules().validate();
        } catch (IllegalArgumentException e) {
            throw new RiskViolation(e.getMessage());
        }
        if (decision.action() == HOLD) return;
        require(account.rules().allowedSymbols().contains(decision.symbol()), "币种不在实验白名单内");
        if (decision.action() == BUY) {
            require(decision.quoteBudgetUsdt().compareTo(account.cashUsdt()) <= 0, "买入预算超过可用现金，禁止借款或追加资金");
        }
    }

    private static BigDecimal buyQuantity(BigDecimal budget, BigDecimal price, BigDecimal feeRate, BigDecimal baseStep) {
        if (budget.signum() <= 0) return BigDecimal.ZERO;
        return step(budget.divide(price.multiply(BigDecimal.ONE.add(feeRate)), 36, RoundingMode.FLOOR),
                baseStep, RoundingMode.FLOOR);
    }

    private static void fresh(long asOf, long now, long maximumAge) {
        require(asOf > 0 && now - asOf <= maximumAge && asOf - now <= 5000, "行情或交易规则已过期，或时间异常");
    }

    private static void positive(BigDecimal number, String label) {
        require(number != null && number.signum() > 0 && number.scale() <= 18 && number.precision() <= 32,
                label + "缺失或数值不支持，拒绝模拟成交");
    }

    static BigDecimal step(BigDecimal value, BigDecimal step, RoundingMode roundingMode) {
        return value.divide(step, 0, roundingMode).multiply(step);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new RiskViolation(reason);
    }
}
