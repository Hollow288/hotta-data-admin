package com.hollow.build.trading.paper;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Set;

/** 买入预算含模拟手续费；卖出使用基础币数量，两者不能混用。 */
public record TradeDecision(Action action, String symbol, BigDecimal quoteBudgetUsdt,
                            BigDecimal baseQuantity, String reason,
                            BigDecimal stopLossPrice, BigDecimal takeProfitPrice) {
    public TradeDecision(Action action, String symbol, BigDecimal quoteBudgetUsdt, BigDecimal baseQuantity, String reason) {
        this(action, symbol, quoteBudgetUsdt, baseQuantity, reason, null, null);
    }
    public enum Action { BUY, SELL, HOLD }

    private static final Set<String> FIELDS = Set.of("action", "symbol", "quoteBudgetUsdt", "baseQuantity", "reason",
            "stopLossPrice", "takeProfitPrice");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public static TradeDecision parse(String reply) {
        try {
            JsonNode root = MAPPER.readTree(reply);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("决策必须是一个 JSON 对象");
            }
            root.fieldNames().forEachRemaining(key -> {
                if (!FIELDS.contains(key)) throw new IllegalArgumentException("未知决策字段: " + key);
            });
            String actionText = requiredText(root, "action");
            Action action = Action.valueOf(actionText);
            String reason = requiredText(root, "reason");
            if (reason.length() > 2000) throw new IllegalArgumentException("reason 不能超过 2000 字符");
            String symbol = root.hasNonNull("symbol") ? requiredText(root, "symbol") : null;
            BigDecimal budget = number(root, "quoteBudgetUsdt");
            BigDecimal quantity = number(root, "baseQuantity");
            TradeDecision decision = new TradeDecision(action, symbol, budget, quantity, reason,
                    number(root, "stopLossPrice"), number(root, "takeProfitPrice"));
            decision.validateShape();
            return decision;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("模型决策不是合法的结构化 JSON", e);
        }
    }

    public void validateShape() {
        if (action == null || reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("决策必须包含有效 action 和 reason");
        }
        if (stopLossPrice != null) positive(stopLossPrice, "stopLossPrice");
        if (takeProfitPrice != null) positive(takeProfitPrice, "takeProfitPrice");
        if (action != Action.BUY && (stopLossPrice != null || takeProfitPrice != null)) {
            throw new IllegalArgumentException("只有 BUY 可以设置持仓止盈止损计划");
        }
        if (stopLossPrice != null && takeProfitPrice != null && stopLossPrice.compareTo(takeProfitPrice) >= 0) {
            throw new IllegalArgumentException("止损价必须低于止盈价");
        }
        if (action == Action.HOLD) {
            if (symbol != null || quoteBudgetUsdt != null || baseQuantity != null) {
                throw new IllegalArgumentException("HOLD 的 symbol 和金额/数量必须为空");
            }
            return;
        }
        if (symbol == null || !symbol.matches("[A-Z0-9]{3,30}")) {
            throw new IllegalArgumentException("交易决策必须包含完整的大写 symbol");
        }
        if (action == Action.BUY) {
            positive(quoteBudgetUsdt, "quoteBudgetUsdt");
            if (baseQuantity != null) throw new IllegalArgumentException("BUY 不能同时指定 baseQuantity");
        } else {
            positive(baseQuantity, "baseQuantity");
            if (quoteBudgetUsdt != null) throw new IllegalArgumentException("SELL 不能同时指定 quoteBudgetUsdt");
        }
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0 || value.scale() > 18 || value.precision() > 32) {
            throw new IllegalArgumentException(name + " 必须是精度不超过 18 位的正数");
        }
    }

    private static String requiredText(JsonNode root, String key) {
        JsonNode node = root.get(key);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(key + " 必须是非空字符串");
        }
        return node.textValue();
    }

    private static BigDecimal number(JsonNode root, String key) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) throw new IllegalArgumentException(key + " 必须是 JSON 数字，不能是字符串");
        return node.decimalValue();
    }
}
