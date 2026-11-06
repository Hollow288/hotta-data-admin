package com.hollow.build.trading.paper;

/** 对调用方可解释的业务状态，如账户不存在、已有运行等。 */
public class PaperTradingException extends RuntimeException {
    private final int code;
    public PaperTradingException(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() { return code; }
}
