package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.TokenSuccessResponseDto;
import com.hollow.build.dto.UserLoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务接口，提供用户认证相关功能
 */
public interface UserService {

    /**
     * 用户登录认证，验证成功后返回Token
     *
     * @param userLoginRequest 用户登录请求DTO，包含用户名和密码
     * @param request HTTP请求对象，用于获取请求上下文信息
     * @return 包含Token信息的响应
     */
    ApiResponse<TokenSuccessResponseDto> login(UserLoginRequestDto userLoginRequest, HttpServletRequest request);
}
