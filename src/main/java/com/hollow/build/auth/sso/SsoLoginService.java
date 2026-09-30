package com.hollow.build.auth.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hollow.build.auth.dto.TokenSuccessResponseDto;
import com.hollow.build.auth.entity.User;
import com.hollow.build.auth.repository.UserMapper;
import com.hollow.build.auth.service.LocalTokenService;
import com.hollow.build.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SsoLoginService {
    public static final String TRANSACTION_COOKIE = "HOTTA_SSO_TX";
    private static final String TRANSACTION_PREFIX = "sso_login:";
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SsoClientProperties properties;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final LocalTokenService localTokenService;

    public record StartResult(URI authorizeUri, ResponseCookie transactionCookie) {
    }

    private record Transaction(String nonce, String redirectPath, String codeVerifier) {
    }

    public StartResult start(String redirectPath) {
        requireEnabled();
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        String codeVerifier = newCodeVerifier();
        String safePath = safeRedirectPath(redirectPath);
        try {
            redisUtil.set(TRANSACTION_PREFIX + state,
                    objectMapper.writeValueAsString(new Transaction(nonce, safePath, codeVerifier)),
                    TRANSACTION_TTL.toSeconds());
        } catch (JsonProcessingException ex) {
            throw new SsoLoginException(500, "无法创建 SSO 登录请求");
        }
        URI authorizeUri = UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl() + "/sso/authorize")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", "openid profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", challengeFor(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        ResponseCookie cookie = ResponseCookie.from(TRANSACTION_COOKIE, state)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(TRANSACTION_TTL)
                .build();
        return new StartResult(authorizeUri, cookie);
    }

    public TokenSuccessResponseDto exchange(String code, String state, String cookieState) {
        requireEnabled();
        if (blank(code) || blank(state) || !state.equals(cookieState)) {
            throw new SsoLoginException(400, "SSO 登录状态校验失败，请重新登录");
        }
        Object stored = redisUtil.getAndDelete(TRANSACTION_PREFIX + state);
        if (!(stored instanceof String storedJson)) {
            throw new SsoLoginException(400, "SSO 登录请求已过期或已使用");
        }
        try {
            Transaction transaction = objectMapper.readValue(storedJson, Transaction.class);
            if (blank(transaction.codeVerifier())) {
                throw new SsoLoginException(400, "SSO 登录请求缺少 PKCE 信息，请重新登录");
            }
            RestClient client = RestClient.create(properties.getInternalBaseUrl());
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", properties.getRedirectUri());
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            form.add("code_verifier", transaction.codeVerifier());
            Map<?, ?> tokens = client.post().uri("/sso/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (tokens == null || !(tokens.get("id_token") instanceof String idToken)
                    || !(tokens.get("access_token") instanceof String ssoAccessToken)) {
                throw new SsoLoginException(401, "SSO 未返回有效令牌");
            }
            Jwt jwt = NimbusJwtDecoder.withJwkSetUri(properties.getInternalBaseUrl() + "/sso/jwks")
                    .build().decode(idToken);
            if (jwt.getIssuer() == null
                    || blank(jwt.getSubject())
                    || !properties.getIssuer().equals(jwt.getIssuer().toString())
                    || !jwt.getAudience().contains(properties.getClientId())
                    || !transaction.nonce().equals(jwt.getClaimAsString("nonce"))) {
                throw new SsoLoginException(401, "SSO 身份令牌校验失败");
            }
            Map<?, ?> userInfo = client.get().uri("/sso/userinfo")
                    .headers(headers -> headers.setBearerAuth(ssoAccessToken))
                    .retrieve().body(Map.class);
            if (userInfo == null || !jwt.getSubject().equals(userInfo.get("sub"))
                    || !(userInfo.get("username") instanceof String username) || blank(username)) {
                throw new SsoLoginException(401, "SSO 用户身份校验失败");
            }
            User user = userMapper.selectByUsername(username);
            if (user == null || !username.equals(user.getUsername()) || !"1".equals(user.getIsEnabled())) {
                throw new SsoLoginException(403, "后台用户不存在或已停用");
            }
            TokenSuccessResponseDto result = localTokenService.issue(user);
            result.setRedirectPath(transaction.redirectPath());
            return result;
        } catch (SsoLoginException ex) {
            throw ex;
        } catch (JsonProcessingException | RestClientException | JwtException ex) {
            throw new SsoLoginException(401, "SSO 登录验证失败，请重试");
        }
    }

    public ResponseCookie clearTransactionCookie() {
        return ResponseCookie.from(TRANSACTION_COOKIE, "")
                .httpOnly(true).secure(properties.isCookieSecure())
                .sameSite("Lax").path("/").maxAge(0).build();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new SsoLoginException(503, "SSO 登录尚未启用");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeFor(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String safeRedirectPath(String value) {
        if (blank(value) || !value.startsWith("/") || value.startsWith("//")
                || value.contains("\\") || value.contains("\r") || value.contains("\n")) {
            return "/dashboard";
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            return "/dashboard";
        }
        return uri.getRawAuthority() == null && uri.getScheme() == null ? value : "/dashboard";
    }
}
