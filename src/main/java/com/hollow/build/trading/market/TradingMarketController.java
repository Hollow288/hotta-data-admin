package com.hollow.build.trading.market;

import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 不经过模型的原始查询入口，便于先验证网络和理解 Tool 下层的普通 Java 服务。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trading/market")
@Tag(name = "Trading Market", description = "Bybit 公开现货行情查询")
public class TradingMarketController {

    private final BybitMarketClient marketClient;

    @GetMapping("/ticker")
    @PublicEndpoint
    @Operation(summary = "查询现货价格和 24 小时变化")
    public ApiResponse<SpotTicker> ticker(@RequestParam String symbol) {
        return ApiResponse.success(marketClient.ticker(symbol));
    }

    @GetMapping("/instrument")
    @PublicEndpoint
    @Operation(summary = "查询现货最低订单金额和精度")
    public ApiResponse<SpotInstrument> instrument(@RequestParam String symbol) {
        return ApiResponse.success(marketClient.instrument(symbol));
    }

    @GetMapping("/klines")
    @PublicEndpoint
    @Operation(summary = "查询现货 K 线，按时间升序返回")
    public ApiResponse<SpotKlines> klines(@RequestParam String symbol,
                                         @RequestParam(defaultValue = "60") String interval,
                                         @RequestParam(defaultValue = "24") int limit) {
        return ApiResponse.success(marketClient.klines(symbol, interval, limit));
    }

    // 保持项目 HTTP 200 + ApiResponse.code 的业务错误约定，并保留可用于排查的错误原因。
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> invalidRequest(IllegalArgumentException e) {
        return new ApiResponse<>(400, e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ApiResponse<Void> invalidParameters(Exception e) {
        return new ApiResponse<>(400, "查询参数缺失或类型错误，请检查 symbol、interval 和 limit");
    }

    @ExceptionHandler(BybitMarketException.class)
    public ApiResponse<Void> upstreamFailure(BybitMarketException e) {
        return new ApiResponse<>(502, e.getMessage());
    }
}
