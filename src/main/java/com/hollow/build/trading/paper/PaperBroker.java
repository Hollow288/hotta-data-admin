package com.hollow.build.trading.paper;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hollow.build.trading.paper.PaperModels.*;
import static com.hollow.build.trading.paper.TradeDecision.Action.BUY;

/** 纯计算的模拟券商：没有 Bybit 下单或账户接口；由账本在数据库事务内保存计算结果。 */
@Service
public class PaperBroker {

    public Execution execute(Account account, RiskControlService.ApprovedOrder order) {
        return execute(account, order, null, null);
    }

    public Execution execute(Account account, RiskControlService.ApprovedOrder order, TradeDecision decision, Long now) {
        List<Position> positions = new ArrayList<>(account.positions());
        Position old = positions.stream().filter(p -> p.symbol().equals(order.symbol())).findFirst()
                .orElse(new Position(order.symbol(), BigDecimal.ZERO, BigDecimal.ZERO));
        positions.removeIf(p -> p.symbol().equals(order.symbol()));
        BigDecimal cashDelta;
        BigDecimal realized = BigDecimal.ZERO;
        if (order.side() == BUY) {
            cashDelta = order.gross().add(order.fee()).negate();
            positions.add(new Position(order.symbol(), old.quantity().add(order.quantity()),
                    old.costBasisUsdt().subtract(cashDelta), plan(old, decision, now)));
        } else {
            if (order.quantity().compareTo(old.quantity()) > 0) throw new IllegalStateException("模拟成交不能产生负持仓");
            BigDecimal allocatedCost = order.quantity().compareTo(old.quantity()) == 0 ? old.costBasisUsdt()
                    : old.costBasisUsdt().multiply(order.quantity()).divide(old.quantity(), 18, RoundingMode.HALF_EVEN);
            cashDelta = order.gross().subtract(order.fee());
            realized = cashDelta.subtract(allocatedCost);
            BigDecimal remaining = old.quantity().subtract(order.quantity());
            if (remaining.signum() > 0) positions.add(new Position(order.symbol(), remaining,
                    old.costBasisUsdt().subtract(allocatedCost), old.plan()));
        }
        BigDecimal cash = account.cashUsdt().add(cashDelta);
        if (cash.signum() < 0) throw new IllegalStateException("模拟成交不能产生负现金");
        Fill fill = new Fill(order.symbol(), order.side(), order.quantity(), order.price(), order.gross(),
                order.fee(), cashDelta, realized);
        return new Execution(cash, account.feesUsdt().add(order.fee()), account.realizedPnlUsdt().add(realized),
                List.copyOf(positions), fill);
    }

    public static Snapshot snapshot(Account account, Execution execution, Map<String, BigDecimal> marks, long now) {
        BigDecimal cash = execution == null ? account.cashUsdt() : execution.cashUsdt();
        BigDecimal fees = execution == null ? account.feesUsdt() : execution.feesUsdt();
        BigDecimal realized = execution == null ? account.realizedPnlUsdt() : execution.realizedPnlUsdt();
        List<Position> positions = execution == null ? account.positions() : execution.positions();
        BigDecimal equity = cash;
        boolean complete = true;
        Map<String, BigDecimal> usedMarks = new LinkedHashMap<>();
        List<PositionValuation> valuations = new ArrayList<>();
        for (Position position : positions) {
            BigDecimal price = marks.get(position.symbol());
            if (price == null || price.signum() <= 0) {
                complete = false;
            } else {
                usedMarks.put(position.symbol(), price);
                equity = equity.add(position.quantity().multiply(price));
            }
            BigDecimal pnl = price == null || price.signum() <= 0 ? null
                    : position.quantity().multiply(price).subtract(position.costBasisUsdt());
            Long opened = position.plan() == null ? null : position.plan().openedAtEpochMillis();
            valuations.add(new PositionValuation(position.symbol(),
                    position.costBasisUsdt().divide(position.quantity(), 18, RoundingMode.HALF_EVEN),
                    usedMarks.get(position.symbol()), pnl,
                    pnl == null || position.costBasisUsdt().signum() == 0 ? null
                            : pnl.multiply(BigDecimal.valueOf(100)).divide(position.costBasisUsdt(), 8, RoundingMode.HALF_EVEN),
                    opened == null ? null : BigDecimal.valueOf(Math.max(0, now - opened))
                            .divide(BigDecimal.valueOf(86_400_000), 6, RoundingMode.HALF_EVEN)));
        }
        return new Snapshot(account.initialCashUsdt(), cash, fees, realized, positions, Map.copyOf(usedMarks),
                complete ? equity : null, complete ? equity.subtract(account.initialCashUsdt()) : null,
                complete, now, List.copyOf(valuations), marks.get("BTCUSDT"));
    }

    private static PositionPlan plan(Position old, TradeDecision decision, Long now) {
        if (decision == null) return old.plan();
        PositionPlan previous = old.plan();
        boolean opening = old.quantity().signum() == 0;
        return new PositionPlan(opening ? now : previous == null ? null : previous.openedAtEpochMillis(),
                opening ? decision.reason() : previous == null ? null : previous.entryThesis(),
                decision.stopLossPrice() != null ? decision.stopLossPrice() : previous == null ? null : previous.stopLossPrice(),
                decision.takeProfitPrice() != null ? decision.takeProfitPrice() : previous == null ? null : previous.takeProfitPrice());
    }
}
