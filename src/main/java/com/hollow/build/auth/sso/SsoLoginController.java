package com.hollow.build.auth.sso;

import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.dto.TokenSuccessResponseDto;
import com.hollow.build.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/sso")
public class SsoLoginController {
    private final SsoLoginService service;

    @PublicEndpoint
    @BypassRateLimit
    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam(value = "redirect", required = false) String redirect) {
        SsoLoginService.StartResult result = service.start(redirect);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(result.authorizeUri())
                .header(HttpHeaders.SET_COOKIE, result.transactionCookie().toString())
                .build();
    }

    @PublicEndpoint
    @BypassRateLimit
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<TokenSuccessResponseDto>> exchange(
            @RequestBody SsoExchangeRequest request,
            @CookieValue(value = SsoLoginService.TRANSACTION_COOKIE, required = false) String transactionCookie) {
        TokenSuccessResponseDto result = service.exchange(request.code(), request.state(), transactionCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, service.clearTransactionCookie().toString())
                .body(ApiResponse.success(result));
    }

    @ExceptionHandler(SsoLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleSsoError(SsoLoginException ex) {
        return ResponseEntity.ok(new ApiResponse<>(ex.getCode(), ex.getMessage()));
    }
}
