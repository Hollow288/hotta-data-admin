package com.hollow.build.trading.paper;

import com.hollow.build.agent.trading.TradingAgent;
import com.hollow.build.trading.config.TradingProperties;
import com.hollow.build.trading.market.BybitMarketClient;
import com.hollow.build.trading.market.BybitMarketException;
import com.hollow.build.trading.market.SpotInstrument;
import com.hollow.build.trading.market.SpotTicker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.hollow.build.trading.paper.PaperModels.*;

/** 单次完整实验流程；手动入口和定时任务均直接复用此服务。 */
@Service
public class PaperTradingService {
    private final PaperLedger ledger;
    private final TradingAgent agent;
    private final BybitMarketClient market;
    private final RiskControlService risk;
    private final PaperBroker broker;
    private final TradingProperties properties;
    private final Clock clock;

    @Autowired
    public PaperTradingService(PaperLedger ledger, TradingAgent agent, BybitMarketClient market,
                               RiskControlService risk, PaperBroker broker, TradingProperties properties) {
        this(ledger, agent, market, risk, broker, properties, Clock.systemUTC());
    }

    PaperTradingService(PaperLedger ledger, TradingAgent agent, BybitMarketClient market,
                         RiskControlService risk, PaperBroker broker, TradingProperties properties, Clock clock) {
        this.ledger = ledger;
        this.agent = agent;
        this.market = market;
        this.risk = risk;
        this.broker = broker;
        this.properties = properties;
        this.clock = clock;
    }

    public Account create(long ownerId, String name, BigDecimal initialCashUsdt) {
        if (name == null || name.isBlank() || name.length() > 64) throw new IllegalArgumentException("name 必填且不超过 64 字符");
        if (initialCashUsdt == null || initialCashUsdt.signum() <= 0 || initialCashUsdt.scale() > 18
                || initialCashUsdt.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("initialCashUsdt 必须是正数且不超过 1000000，单位为虚拟 USDT，不自动换算人民币");
        }
        return ledger.create(ownerId, name.strip(), initialCashUsdt, Rules.from(properties.getPaper()), clock.millis());
    }

    public Account account(long ownerId, String id) { return ledger.account(ownerId, id); }
    public List<Account> accounts(long ownerId) { return ledger.accounts(ownerId); }
    public Run run(long ownerId, String accountId, String runId) { return ledger.run(ownerId, accountId, runId); }
    public List<Run> runs(long ownerId, String accountId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit 必须在 1～100 之间");
        return ledger.runs(ownerId, accountId, limit);
    }

    public List<EquitySample> equitySamples(long ownerId, String accountId, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit 必须在 1～1000 之间");
        return ledger.equitySamples(ownerId, accountId, limit);
    }

    public Run runOnce(long ownerId, String accountId, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw new IllegalArgumentException("idempotencyKey 必填，最多 64 位，使用字母数字及 . _ : -");
        }
        Reservation reservation = ledger.begin(ownerId, accountId, idempotencyKey.toLowerCase(Locale.ROOT), clock.millis());
        if (!reservation.created()) return reservation.run();
        Run run = reservation.run();
        Account account = reservation.account();
        TradingAgent.DecisionOutcome outcome;
        SpotTicker executionTicker = null;
        SpotInstrument executionInstrument = null;
        Map<String, SpotTicker> quotes = new HashMap<>();
        try {
            loadHoldingQuotes(account, quotes);
            // 空仓也采集 BTC 基准；失败不阻止决策，不构造虚假价格。
            try { if (!quotes.containsKey("BTCUSDT")) quotes.put("BTCUSDT", market.ticker("BTCUSDT")); } catch (BybitMarketException ignored) { }
            Snapshot before = PaperBroker.snapshot(account, null, valuationMarks(quotes, account.rules(), clock.millis()), clock.millis());
            if (!ledger.recordBefore(ownerId, accountId, run.runId(), before, clock.millis())) {
                throw new PaperTradingException(409, "运行已超时或失去执行权");
            }
            List<RunSummary> recent = ledger.runs(ownerId, accountId, 6).stream()
                    .filter(r -> r.status() != RunStatus.RUNNING).limit(5)
                    .map(r -> new RunSummary(r.runId(), r.status(), r.decision(), r.fill(), r.error(), r.completedAtEpochMillis())).toList();
            DecisionContext context = new DecisionContext(before, account.rules(), recent,
                    "仅模拟。选择一条 BUY/SELL/HOLD；含费用预算不能超过现金，不得追加本金。maxTradesPerDay 只限制买入；SELL 减仓豁免次数。持仓 plan 为持续有效的建仓记忆，不因 recentRuns 滚动失效。");
            outcome = agent.decide(context, run.runId(), clientIp);
        } catch (Exception e) {
            List<String> trace = e instanceof TradingAgent.DecisionFailure failure ? failure.debugTrace() : List.of();
            return failure(ownerId, accountId, run.runId(), null, trace, e);
        }
        try {
            risk.validateIntent(outcome.decision(), account);
            if (outcome.decision().action() != TradeDecision.Action.HOLD) {
                executionTicker = market.ticker(outcome.decision().symbol());
                executionInstrument = market.instrument(outcome.decision().symbol());
            }
            quotes.clear();
            if (executionTicker != null) quotes.put(executionTicker.symbol(), executionTicker);
            loadHoldingQuotes(account, quotes);
            if (!quotes.containsKey("BTCUSDT")) {
                try { quotes.put("BTCUSDT", market.ticker("BTCUSDT")); } catch (BybitMarketException ignored) { }
            }
        } catch (RiskControlService.RiskViolation e) {
            return ledger.complete(ownerId, accountId, run.runId(), clock::millis, state -> new Completion(
                    RunStatus.REJECTED, outcome.decision(), null, PaperBroker.snapshot(state.account(), null, Map.of(), state.nowEpochMillis()),
                    clip(e.getMessage()), outcome.debugTrace()));
        } catch (Exception e) {
            return failure(ownerId, accountId, run.runId(), outcome.decision(), outcome.debugTrace(), e);
        }
        SpotTicker ticker = executionTicker;
        SpotInstrument instrument = executionInstrument;
        return ledger.complete(ownerId, accountId, run.runId(), clock::millis, state -> {
            long now = state.nowEpochMillis();
            Map<String, BigDecimal> marks = valuationMarks(quotes, state.account().rules(), now);
            try {
                var order = risk.validate(outcome.decision(), state.account(), ticker, instrument, state.tradesToday(), now);
                Execution execution = order == null ? null : broker.execute(state.account(), order, outcome.decision(), now);
                return new Completion(execution == null ? RunStatus.HOLD : RunStatus.FILLED, outcome.decision(), execution,
                        PaperBroker.snapshot(state.account(), execution, marks, now), null, outcome.debugTrace());
            } catch (RiskControlService.RiskViolation e) {
                return new Completion(RunStatus.REJECTED, outcome.decision(), null,
                        PaperBroker.snapshot(state.account(), null, marks, now), clip(e.getMessage()), outcome.debugTrace());
            } catch (RuntimeException e) {
                return new Completion(RunStatus.ERROR, outcome.decision(), null,
                        PaperBroker.snapshot(state.account(), null, marks, now), clip(e.getMessage()), outcome.debugTrace());
            }
        });
    }

    private void loadHoldingQuotes(Account account, Map<String, SpotTicker> quotes) {
        for (Position position : account.positions()) {
            if (!quotes.containsKey(position.symbol())) {
                try { quotes.put(position.symbol(), market.ticker(position.symbol())); }
                catch (BybitMarketException ignored) { /* 保留未知估值，不能以成本代替市价。 */ }
            }
        }
    }

    static Map<String, BigDecimal> valuationMarks(Map<String, SpotTicker> quotes, Rules rules, long now) {
        Map<String, BigDecimal> marks = new HashMap<>();
        for (SpotTicker ticker : quotes.values()) {
            if (ticker == null) continue;
            long age = now - ticker.asOfEpochMillis();
            if (ticker.lastPrice() != null && ticker.lastPrice().signum() > 0 && age <= rules.maxMarketAgeMillis() && age >= -5000) {
                marks.put(ticker.symbol(), ticker.lastPrice());
            }
        }
        return marks;
    }

    private Run failure(long ownerId, String accountId, String runId, TradeDecision decision,
                        List<String> trace, Exception error) {
        return ledger.complete(ownerId, accountId, runId, clock::millis, state -> new Completion(RunStatus.ERROR, decision, null,
                PaperBroker.snapshot(state.account(), null, Map.of(), state.nowEpochMillis()), clip(error.getMessage()), trace));
    }

    private static String clip(String text) {
        if (text == null || text.isBlank()) return "实验执行失败，未产生模拟成交";
        return text.substring(0, Math.min(text.length(), 1800));
    }
}
