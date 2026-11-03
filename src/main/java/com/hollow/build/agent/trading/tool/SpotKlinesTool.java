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
public class SpotKlinesTool implements TradingTool {

    private final BybitMarketClient marketClient;

    @Override
    public String name() {
        return "spot_klines";
    }

    @Override
    public String description() {
        return "查询 Bybit 现货 K 线。interval 数字单位为分钟，D/W/M 分别为日/周/月；默认最近 24 根一小时线。"
                + "结果按时间升序；closed=false 的 K 线尚未结束，不能当作已完成周期。最多 200 根。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of(
                        "symbol", TradingTool.symbolSchema(),
                        "interval", Map.of("type", "string", "enum", BybitMarketClient.SUPPORTED_INTERVALS,
                                "description", "K 线周期，默认 60"),
                        "limit", Map.of("type", "integer", "minimum", 1,
                                "maximum", BybitMarketClient.MAX_KLINE_LIMIT, "description", "根数，默认 24")),
                "required", List.of("symbol"), "additionalProperties", false);
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String interval = arguments.containsKey("interval")
                ? TradingTool.requiredString(arguments, "interval") : "60";
        return JSON.toJSONString(marketClient.klines(TradingTool.requiredString(arguments, "symbol"),
                interval, TradingTool.integerArgument(arguments, "limit", 24)));
    }
}
