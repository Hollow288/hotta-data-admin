package com.hollow.build.agent.trading.tool;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.trading.TradingTool;
import com.hollow.build.trading.market.BybitMarketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SpotInstrumentTool implements TradingTool {

    private final BybitMarketClient marketClient;

    @Override
    public String name() {
        return "spot_instrument";
    }

    @Override
    public String description() {
        return "查询 Bybit 现货最低订单金额、数量/金额步长、价格步长和交易状态。"
                + "minOrderAmount 的单位是 quoteCoin（如 USDT），不能直接当成人民币。"
                + "basePrecision、quotePrecision、tickSize 是步长，不是小数位数。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of("symbol", TradingTool.symbolSchema()),
                "required", List.of("symbol"), "additionalProperties", false);
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return JSON.toJSONString(marketClient.instrument(TradingTool.requiredString(arguments, "symbol")));
    }
}
