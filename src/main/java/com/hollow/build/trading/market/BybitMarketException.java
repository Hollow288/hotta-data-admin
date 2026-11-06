package com.hollow.build.trading.market;

/** 网络、Bybit 业务错误和不完整的上游数据统一由此异常表达。 */
public class BybitMarketException extends RuntimeException {

    public BybitMarketException(String message) {
        super(message);
    }

    public BybitMarketException(String message, Throwable cause) {
        super(message, cause);
    }
}
