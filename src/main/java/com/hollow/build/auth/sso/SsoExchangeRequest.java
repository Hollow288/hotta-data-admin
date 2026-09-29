package com.hollow.build.auth.sso;

public record SsoExchangeRequest(String code, String state) {
}
