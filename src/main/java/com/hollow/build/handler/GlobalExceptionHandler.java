package com.hollow.build.handler;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.agent.config.AgentDisabledException;
import com.hollow.build.agent.config.AgentUnsupportedException;
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

/**
 * 全局异常处理器，统一捕获并处理控制器层抛出的异常。
 * <p>将异常信息封装为统一的 {@link ApiResponse} 格式返回给客户端。</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理认证凭证未找到异常（401 未授权）。
     *
     * @param response HTTP 响应对象
     * @param ex       认证凭证未找到异常
     * @throws IOException 写入响应时可能抛出的 IO 异常
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public void handleAuthenticationException(HttpServletResponse response, AuthenticationCredentialsNotFoundException ex) throws IOException {
        logger.error("Authentication error: ", ex);
        response.setStatus(HttpServletResponse.SC_OK);  // 返回401
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        response.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 处理访问拒绝异常（403 权限不足）。
     *
     * @param response HTTP 响应对象
     * @param ex       访问拒绝异常
     * @throws IOException 写入响应时可能抛出的 IO 异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(HttpServletResponse response, AccessDeniedException ex) throws IOException {
        logger.error("Access denied: ", ex);
        response.setStatus(HttpServletResponse.SC_OK);  // 返回403
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.FORBIDDEN.getCode(), GlobalErrorCodeConstants.FORBIDDEN.getMsg());
        response.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 处理 agent 被运营开关关闭时抛出的异常，返回 501 "功能未实现/未开启"。
     */
    @ExceptionHandler(AgentDisabledException.class)
    public void handleAgentDisabled(HttpServletResponse response, AgentDisabledException ex) throws IOException {
        logger.warn("Agent disabled: {}", ex.getAgentName());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(
                GlobalErrorCodeConstants.NOT_IMPLEMENTED.getCode(),
                "agent 已关闭: " + ex.getAgentName());
        response.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 处理 Router 明确判定当前没有合适 agent 可处理的情况。
     */
    @ExceptionHandler(AgentUnsupportedException.class)
    public void handleAgentUnsupported(HttpServletResponse response, AgentUnsupportedException ex) throws IOException {
        logger.info("Agent unsupported request: {}", ex.getReason());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ApiResponse<Object> result = new ApiResponse<>(
                GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                ex.getMessage());
        response.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 处理所有未被其他处理器捕获的异常（500 系统异常）。
     *
     * @param response HTTP 响应对象
     * @param ex       未处理的异常
     * @throws IOException 写入响应时可能抛出的 IO 异常
     */
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
