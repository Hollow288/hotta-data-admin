package com.hollow.build.agent.trading.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.trading.TradingTool;
import com.hollow.build.trading.market.TechnicalIndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TechnicalSummaryTool implements TradingTool {
    private final TechnicalIndicatorService indicators;
    @Override public String name() { return "technical_summary"; }
    @Override public String description() {
        return "Java预计算Bybit现货1H/4H/1D技术摘要：EMA20/50/200、RSI14、MACD12/26/9、ATR14、布林带位置、量比及前20根高低点。"
                + "仅已收盘线；优先使用此工具分析趋势，避免心算原始OHLCV。指标是历史描述，不保证未来收益。";
    }
    @Override public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of("symbol", TradingTool.symbolSchema()),
                "required", List.of("symbol"), "additionalProperties", false);
    }
    @Override public String execute(Map<String, Object> arguments) {
        return JSON.toJSONString(indicators.summary(TradingTool.requiredString(arguments, "symbol")));
    }
}
