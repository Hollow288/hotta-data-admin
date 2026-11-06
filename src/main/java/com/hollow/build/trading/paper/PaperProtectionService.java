package com.hollow.build.trading.paper;

import com.hollow.build.trading.market.BybitMarketClient;
import com.hollow.build.trading.market.SpotTicker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hollow.build.trading.paper.PaperModels.*;

/** 纯 Java 保护性减仓：不调用模型，仍经过账户执行权、行情时效、风控、费用和账本事务。 */
@Slf4j
@Service
public class PaperProtectionService {
    private final PaperLedger ledger;
    private final BybitMarketClient market;
    private final RiskControlService risk;
    private final PaperBroker broker;
    private final Clock clock;

    @Autowired
    public PaperProtectionService(PaperLedger ledger, BybitMarketClient market, RiskControlService risk, PaperBroker broker) {
        this(ledger, market, risk, broker, Clock.systemUTC());
    }

    PaperProtectionService(PaperLedger ledger, BybitMarketClient market, RiskControlService risk, PaperBroker broker, Clock clock) {
        this.ledger = ledger;
        this.market = market;
        this.risk = risk;
        this.broker = broker;
        this.clock = clock;
    }

    public List<Run> check(long ownerId, String accountId) {
        List<Run> results = new ArrayList<>();
        for (Position initial : ledger.account(ownerId, accountId).positions()) {
            Account account = ledger.account(ownerId, accountId);
            Position position = position(account, initial.symbol());
            if (position == null || position.plan() == null
                    || (position.plan().stopLossPrice() == null && position.plan().takeProfitPrice() == null)) continue;
            SpotTicker observed;
            try {
                observed = market.ticker(position.symbol());
            } catch (com.hollow.build.trading.market.BybitMarketException error) {
                Reservation failed = ledger.begin(ownerId, accountId, "protect:" + account.version() + ":" + position.symbol(), clock.millis());
                results.add(failed.created() ? ledger.complete(ownerId, accountId, failed.run().runId(), clock::millis,
                        state -> new Completion(RunStatus.ERROR, null, null,
                                PaperBroker.snapshot(state.account(), null, Map.of(), state.nowEpochMillis()), clip(error.getMessage()), List.of()))
                        : failed.run());
                continue; // 某一交易对断网不能让同账户其他持仓失去检查机会。
            }
            if (trigger(position, observed, account.rules(), clock.millis()) == null) continue;
            // 同一账户版本、同一持仓的并发巡检复用一次运行；版本变化必须重新评估。
            String key = "protect:" + account.version() + ":" + position.symbol();
            Reservation reserved = ledger.begin(ownerId, accountId, key, clock.millis());
            if (!reserved.created()) { results.add(reserved.run()); continue; }
            Run run = reserved.run();
            TradeDecision decision = null;
            Map<String, SpotTicker> quotes = new HashMap<>();
            try {
                SpotTicker ticker = market.ticker(position.symbol());
                var instrument = market.instrument(position.symbol());
                quotes.put(position.symbol(), ticker);
                for (Position holding : reserved.account().positions()) {
                    if (!quotes.containsKey(holding.symbol())) {
                        try { quotes.put(holding.symbol(), market.ticker(holding.symbol())); }
                        catch (RuntimeException e) { log.warn("保护性估值暂缺：{}", holding.symbol()); }
                    }
                }
                Snapshot before = PaperBroker.snapshot(reserved.account(), null,
                        PaperTradingService.valuationMarks(quotes, reserved.account().rules(), clock.millis()), clock.millis());
                if (!ledger.recordBefore(ownerId, accountId, run.runId(), before, clock.millis())) {
                    throw new PaperTradingException(409, "保护性运行已超时或失去执行权");
                }
                Position current = position(reserved.account(), position.symbol());
                String reason = reserved.account().version() == account.version()
                        ? trigger(current, ticker, account.rules(), clock.millis()) : null;
                if (reason == null) {
                    decision = hold("持仓版本变化或最新报价未触发保护条件，本次跳过");
                } else {
                    if (instrument.basePrecision() == null || instrument.basePrecision().signum() <= 0
                            || instrument.maxMarketOrderQty() == null || instrument.maxMarketOrderQty().signum() <= 0) {
                        throw new RiskControlService.RiskViolation("保护性卖出缺少有效数量规则");
                    }
                    BigDecimal quantity = RiskControlService.step(current.quantity().min(instrument.maxMarketOrderQty()),
                            instrument.basePrecision(), RoundingMode.FLOOR);
                    decision = new TradeDecision(TradeDecision.Action.SELL, current.symbol(), null, quantity, reason);
                }
                TradeDecision planned = decision;
                results.add(ledger.complete(ownerId, accountId, run.runId(), clock::millis, state -> {
                    Map<String, BigDecimal> marks = PaperTradingService.valuationMarks(quotes, state.account().rules(), state.nowEpochMillis());
                    try {
                        // 锁内再次校验触发条件和行情时效，不能让等待账户锁延长报价寿命。
                        if (planned.action() == TradeDecision.Action.SELL
                                && trigger(position(state.account(), planned.symbol()), ticker, state.account().rules(), state.nowEpochMillis()) == null) {
                            return new Completion(RunStatus.HOLD, hold("保护条件已失效或行情过期"), null,
                                    PaperBroker.snapshot(state.account(), null, marks, state.nowEpochMillis()), null, List.of());
                        }
                        var order = risk.validate(planned, state.account(), ticker, instrument, state.tradesToday(), state.nowEpochMillis());
                        Execution execution = order == null ? null : broker.execute(state.account(), order, planned, state.nowEpochMillis());
                        return new Completion(execution == null ? RunStatus.HOLD : RunStatus.FILLED, planned, execution,
                                PaperBroker.snapshot(state.account(), execution, marks, state.nowEpochMillis()), null, List.of("纯 Java 保护性巡检，无模型调用"));
                    } catch (RiskControlService.RiskViolation error) {
                        return new Completion(RunStatus.REJECTED, planned, null,
                                PaperBroker.snapshot(state.account(), null, marks, state.nowEpochMillis()), error.getMessage(), List.of());
                    }
                }));
            } catch (RuntimeException error) {
                TradeDecision attempted = decision;
                results.add(ledger.complete(ownerId, accountId, run.runId(), clock::millis, state -> new Completion(
                        error instanceof RiskControlService.RiskViolation ? RunStatus.REJECTED : RunStatus.ERROR,
                        attempted, null, PaperBroker.snapshot(state.account(), null, Map.of(), state.nowEpochMillis()),
                        clip(error.getMessage()), List.of())));
            }
        }
        return List.copyOf(results);
    }

    /** 两字段均为替换语义，null 清除对应阈值；可为已有持仓补设计划，历史建仓信息不伪造。 */
    public Position configure(long ownerId, String accountId, String symbol, BigDecimal stop, BigDecimal target) {
        new TradeDecision(TradeDecision.Action.BUY, symbol, BigDecimal.ONE, null, "修改保护计划", stop, target).validateShape();
        return ledger.updateProtection(ownerId, accountId, symbol, stop, target);
    }

    /** 使用可卖出的买一价触发；阈值只是触发条件，成交仍使用实时买一价、滑点和手续费。 */
    static String trigger(Position position, SpotTicker ticker, Rules rules, long now) {
        if (position == null || position.plan() == null || ticker == null || !position.symbol().equals(ticker.symbol())
                || ticker.bidPrice() == null || ticker.bidPrice().signum() <= 0
                || ticker.asOfEpochMillis() <= 0 || now - ticker.asOfEpochMillis() > rules.maxMarketAgeMillis()
                || ticker.asOfEpochMillis() - now > 5000) return null;
        PositionPlan plan = position.plan();
        if (plan.stopLossPrice() != null && ticker.bidPrice().compareTo(plan.stopLossPrice()) <= 0) return "保护性止损：买一价触及持仓止损线";
        if (plan.takeProfitPrice() != null && ticker.bidPrice().compareTo(plan.takeProfitPrice()) >= 0) return "保护性止盈：买一价触及持仓止盈线";
        return null;
    }

    private static Position position(Account account, String symbol) {
        return account.positions().stream().filter(p -> p.symbol().equals(symbol)).findFirst().orElse(null);
    }
    private static TradeDecision hold(String reason) { return new TradeDecision(TradeDecision.Action.HOLD, null, null, null, reason); }
    private static String clip(String text) { return text == null ? "保护性巡检失败" : text.substring(0, Math.min(1800, text.length())); }
}
