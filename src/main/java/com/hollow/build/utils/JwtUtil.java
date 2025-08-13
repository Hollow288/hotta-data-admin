package com.hollow.build.utils;

import com.hollow.build.config.TokenConfigurationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    private final TokenConfigurationProperties tokenConfigurationProperties;

    public JwtUtil(TokenConfigurationProperties tokenConfigurationProperties) {
        this.tokenConfigurationProperties = tokenConfigurationProperties;
    }

    /**
     * 获取随机UUID
     */
    public String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 生成JWT，默认有效期为AccessToken TTL
     */
    public String createJWT(String subject) {
        return createJWT(subject, null);
    }

    /**
     * 生成JWT，可指定TTL
     */
    public String createJWT(String subject, Long ttlMillis) {
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, getUUID());
        return builder.compact();
    }

    /**
     * 生成JWT，可指定id和TTL
     */
    public String createJWT(String id, String subject, Long ttlMillis) {
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, id);
        return builder.compact();
    }

    /**
     * 创建JWT构建器
     */
    private JwtBuilder getJwtBuilder(String subject, Long ttlMillis, String uuid) {
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        if (ttlMillis == null) {
            ttlMillis = getAccessTokenTTL();
        }

        long expMillis = nowMillis + ttlMillis;
        Date expDate = new Date(expMillis);

        return Jwts.builder()
                .id(uuid)
                .subject(subject)
                .issuer("sg")
                .issuedAt(now)
                .signWith(secretKey, Jwts.SIG.HS256)
                .expiration(expDate);
    }

    /**
     * 使用配置类的secretKey生成SecretKey
     */
    public SecretKey generalKey() {
        byte[] encodedKey = Base64.getDecoder().decode(tokenConfigurationProperties.getSecretKey());
        return new SecretKeySpec(encodedKey, 0, encodedKey.length, "HmacSHA256");
    }

    /**
     * 解析JWT
     */
    public Claims parseJWT(String jwt) throws Exception {
        SecretKey secretKey = generalKey();
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    /**
     * 判断Token是否过期
     */
    public Boolean isTokenExpired(String token) throws Exception {
        return getExpirationDate(token).before(new Date());
    }

    /**
     * 获取Token过期时间
     */
    public Date getExpirationDate(String token) throws Exception {
        return parseJWT(token).getExpiration();
    }

    /**
     * 获取AccessToken有效期（毫秒）
     */
    public long getAccessTokenTTL() {
        return tokenConfigurationProperties.getValidityAccessToken() * 60 * 1000L;
    }

    /**
     * 获取RefreshToken有效期（毫秒）
     */
    public long getRefreshTokenTTL() {
        return tokenConfigurationProperties.getValidityRefreshToken() * 60 * 1000L;
    }
}
