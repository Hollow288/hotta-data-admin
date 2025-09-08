package com.hollow.build.utils;

import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.TokenConfigurationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

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


    public void  returnTokenError(HttpServletResponse response) throws IOException {
        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write(JSONObject.toJSONString(result));
    }


    public long getUsernameMaxAttempt() {
        return tokenConfigurationProperties.getUsernameMaxAttempt();
    }


    public long getIpMaxAttempt() {
        return tokenConfigurationProperties.getIpMaxAttempt();
    }

    public long getTtlSeconds() {
        return tokenConfigurationProperties.getTtlSeconds() * 60;
    }

}
