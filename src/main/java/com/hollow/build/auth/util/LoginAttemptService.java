package com.hollow.build.auth.util;

import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.auth.config.TokenConfigurationProperties;
import com.hollow.build.utils.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录尝试控制服务，负责记录失败次数、判断封禁状态并输出认证失败响应。
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private final RedisUtil redisUtil;

    private final TokenConfigurationProperties tokenConfigurationProperties;


    /**
     * 获取客户端真实 IP
     */
    public String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 登录失败，增加计数
     */
    public void recordFailedAttempt(String username, String ip) {
        String usernameKey = "login_fail:" + username;
        String ipKey = "login_fail_ip:" + ip;

        String userFailStr = (String) redisUtil.get(usernameKey);
        String ipFailStr = (String) redisUtil.get(ipKey);

        int userFailCount = userFailStr == null ? 0 : Integer.parseInt(userFailStr);
        int ipFailCount = ipFailStr == null ? 0 : Integer.parseInt(ipFailStr);

        redisUtil.set(usernameKey, String.valueOf(userFailCount + 1), getTtlSeconds());
        redisUtil.set(ipKey, String.valueOf(ipFailCount + 1), getTtlSeconds());
    }

    /**
     * 登录成功，清除计数
     */
    public void clearAttempts(String username) {
        String usernameKey = "login_fail:" + username;
        redisUtil.removeKey(usernameKey);
    }

    /**
     * 判断是否超过最大尝试次数
     */
    public boolean isBlocked(String username, String ip) {
        String usernameKey = "login_fail:" + username;
        String ipKey = "login_fail_ip:" + ip;

        String userFailStr = (String) redisUtil.get(usernameKey);
        String ipFailStr = (String) redisUtil.get(ipKey);

        int userFailCount = userFailStr == null ? 0 : Integer.parseInt(userFailStr);
        int ipFailCount = ipFailStr == null ? 0 : Integer.parseInt(ipFailStr);

        return userFailCount >= getUsernameMaxAttempt() || ipFailCount >= getIpMaxAttempt();
    }


    /**
     * 向客户端输出未认证错误响应。
     *
     * @param response HTTP 响应对象
     * @throws IOException 写入响应体时可能抛出的异常
     */
    public void  returnTokenError(HttpServletResponse response) throws IOException {
        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write(JSONObject.toJSONString(result));
    }


    /**
     * 获取单个用户名允许的最大失败次数。
     *
     * @return 用户名维度的最大失败次数
     */
    public long getUsernameMaxAttempt() {
        return tokenConfigurationProperties.getUsernameMaxAttempt();
    }


    /**
     * 获取单个 IP 允许的最大失败次数。
     *
     * @return IP 维度的最大失败次数
     */
    public long getIpMaxAttempt() {
        return tokenConfigurationProperties.getIpMaxAttempt();
    }

    /**
     * 获取登录失败计数在 Redis 中的过期时间。
     *
     * @return 失败计数过期时间，单位为秒
     */
    public long getTtlSeconds() {
        return tokenConfigurationProperties.getTtlSeconds() * 60;
    }

}
