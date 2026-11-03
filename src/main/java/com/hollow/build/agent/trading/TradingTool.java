package com.hollow.build.agent.trading;

import com.hollow.build.agent.core.Tool;

import java.math.BigDecimal;
import java.util.Map;

/** 标记接口：Spring 只把此类型工具装入 tradingToolRegistry。 */
public interface TradingTool extends Tool {

    static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " 必须是非空字符串");
        }
        return text.strip();
    }

    static int integerArgument(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null && !arguments.containsKey(key)) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException ignored) {
                // 小数和溢出都拒绝，不能静默截断后改变模型原本的查询。
            }
        }
        throw new IllegalArgumentException(key + " 必须是整数");
    }

    static Map<String, Object> symbolSchema() {
        return Map.of("type", "string", "description", "完整现货交易对代码，例如 BTCUSDT、ETHUSDT");
    }
}
