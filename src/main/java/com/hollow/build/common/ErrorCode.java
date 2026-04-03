package com.hollow.build.common;


import lombok.Data;


/**
 * 通用错误码对象，封装业务返回中的状态码与提示信息。
 */
@Data
public class ErrorCode {

    /**
     * 错误码
     */
    private final Integer code;
    /**
     * 错误提示
     */
    private final String msg;

    /**
     * 创建错误码对象。
     *
     * @param code 错误状态码
     * @param message 错误提示信息
     */
    public ErrorCode(Integer code, String message) {
        this.code = code;
        this.msg = message;
    }

}
