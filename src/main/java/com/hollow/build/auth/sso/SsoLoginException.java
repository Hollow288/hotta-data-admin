package com.hollow.build.auth.sso;

public class SsoLoginException extends RuntimeException {
    private final int code;

    public SsoLoginException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
