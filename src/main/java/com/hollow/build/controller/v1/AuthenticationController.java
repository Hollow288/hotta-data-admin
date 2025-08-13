package com.hollow.build.controller.v1;


import com.hollow.build.common.ApiResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "登录", description = "登录相关接口")
public class AuthenticationController {

	private final UserService userService;


	@PublicEndpoint
	@PostMapping(value = "/auth/login")
	@Operation(summary = "Validates user login credentials", description = "Validates user login credentials and returns access-token on successful authentication")
	public ApiResponse<TokenSuccessResponseDto> login(@RequestBody final UserLoginRequestDto userLoginRequest, HttpServletRequest request) {
		return userService.login(userLoginRequest,request);
	}

}