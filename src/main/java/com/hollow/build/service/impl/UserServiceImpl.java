package com.hollow.build.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.dto.CustomUserDetails;
import com.hollow.build.dto.TokenSuccessResponseDto;
import com.hollow.build.dto.UserLoginRequestDto;
import com.hollow.build.service.UserService;
import com.hollow.build.utils.JwtUtil;
import com.hollow.build.utils.LoginAttemptService;
import com.hollow.build.utils.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final AuthenticationManager authenticationManager; // 直接注入
	private final RedisUtil redisUtil;
	private final JwtUtil jwtUtil;
	private final LoginAttemptService loginAttemptService;

	@Override
	public ApiResponse<TokenSuccessResponseDto> login(UserLoginRequestDto userLoginRequest, HttpServletRequest request) {

		String ip = loginAttemptService.getClientIP(request);

		if (loginAttemptService.isBlocked(userLoginRequest.getUsername(), ip)) {
			return new ApiResponse<>(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getCode(),
					GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getMsg());
		}

		UsernamePasswordAuthenticationToken authenticationToken =
				new UsernamePasswordAuthenticationToken(
						userLoginRequest.getUsername(),
						userLoginRequest.getPassword()
				);

		Authentication authenticate;
		try {
			authenticate = authenticationManager.authenticate(authenticationToken);
		} catch (AuthenticationException e) {
			loginAttemptService.recordFailedAttempt(userLoginRequest.getUsername(), ip);
			log.error("用户 {} 登录失败: {}", userLoginRequest.getUsername(), e.getMessage());
			return new ApiResponse<>(
					GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
					"验证失败"
			);
		}

		CustomUserDetails loginUser = (CustomUserDetails) authenticate.getPrincipal();

		String userid = loginUser.getUser().getUserId().toString();
		String accessToken = jwtUtil.createJWT(userid, jwtUtil.getAccessTokenTTL());
		String refreshToken = jwtUtil.createJWT(userid, jwtUtil.getRefreshTokenTTL());

		redisUtil.set(
				"access_token:" + userid,
				JSONObject.toJSONString(loginUser.getPermissions()),
				jwtUtil.getAccessTokenTTL() / 1000
		);

		redisUtil.set(
				"refresh_token:" + userid,
				JSONObject.toJSONString(loginUser.getPermissions()),
				jwtUtil.getRefreshTokenTTL() / 1000
		);

		// 登录成功
		loginAttemptService.clearAttempts(userLoginRequest.getUsername());

		return ApiResponse.success(
				TokenSuccessResponseDto.builder()
						.accessToken(accessToken)
						.refreshToken(refreshToken)
						.build()
		);
	}
}