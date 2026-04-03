package com.hollow.build.handler;

import com.hollow.build.utils.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自定义认证入口点，用于处理未认证用户访问受保护资源时的响应。
 * <p>当用户未通过身份验证时，返回 Token 错误信息。</p>
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final LoginAttemptService loginAttemptService;

    /**
     * 处理认证失败的请求，向客户端返回 Token 错误响应。
     *
     * @param request       HTTP 请求对象
     * @param response      HTTP 响应对象
     * @param authException 认证异常信息
     * @throws IOException 写入响应时可能抛出的 IO 异常
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        loginAttemptService.returnTokenError(response);
    }
}

