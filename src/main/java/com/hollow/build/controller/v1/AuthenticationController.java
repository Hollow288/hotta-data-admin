package com.hollow.build.controller.v1;


import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.TokenSuccessResponseDto;
import com.hollow.build.dto.UserLoginRequestDto;
import com.hollow.build.service.UserService;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器，提供用户登录认证相关接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "登录", description = "登录相关接口")
public class AuthenticationController {

	private final UserService userService;

	/**
	 * 用户登录接口，验证用户凭证并返回访问令牌
	 *
	 * @param userLoginRequest 用户登录请求，包含用户名和密码
	 * @param request HTTP 请求对象
	 * @return 登录成功后的令牌信息
	 */
	@PublicEndpoint
	@BypassRateLimit
	@PostMapping(value = "/auth/login")
	@Operation(summary = "Validates user login credentials", description = "Validates user login credentials and returns access-token on successful authentication")
	public ApiResponse<TokenSuccessResponseDto> login(@RequestBody final UserLoginRequestDto userLoginRequest, HttpServletRequest request) {
		return userService.login(userLoginRequest,request);
	}

}