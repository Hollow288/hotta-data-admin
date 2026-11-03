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
public class SpotTickerTool implements TradingTool {

    private final BybitMarketClient marketClient;

    @Override
    public String name() {
        return "spot_ticker";
    }

    @Override
    public String description() {
        return "查询 Bybit 指定现货交易对的最新价格、买一卖一价、24 小时涨跌百分数、成交量和数据时间。"
                + "change24hPercent=1.25 表示上涨 1.25%，不是 125%。不含账户余额。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of("symbol", TradingTool.symbolSchema()),
                "required", List.of("symbol"), "additionalProperties", false);
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return JSON.toJSONString(marketClient.ticker(TradingTool.requiredString(arguments, "symbol")));
    }
}
