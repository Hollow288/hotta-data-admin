package com.hollow.build.handler;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public void handleAuthenticationException(HttpServletResponse response, AuthenticationCredentialsNotFoundException ex) throws IOException {
        logger.error("Authentication error: ", ex);
        response.setStatus(HttpServletResponse.SC_OK);  // 返回401
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        response.getWriter().write(JSON.toJSONString(result));
    }

    // 处理403权限不足异常
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(HttpServletResponse response, AccessDeniedException ex) throws IOException {
        logger.error("Access denied: ", ex);
        response.setStatus(HttpServletResponse.SC_OK);  // 返回403
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.FORBIDDEN.getCode(), GlobalErrorCodeConstants.FORBIDDEN.getMsg());
        response.getWriter().write(JSON.toJSONString(result));
    }

    @ExceptionHandler(Exception.class)
    public void handleException(HttpServletResponse response,Exception ex) throws IOException {
        logger.error("Unhandled exception: ", ex);
        // 自定义返回内容
        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg());
        // 设置响应内容
        response.setStatus(HttpServletResponse.SC_OK);  // 返回状态码200
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write(JSON.toJSONString(result));  // 将 Map 转为 JSON 并写入响应
    }
}
