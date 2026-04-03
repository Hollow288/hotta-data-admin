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

/**
 * 用户服务实现类，负责处理登录认证和令牌缓存逻辑。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final AuthenticationManager authenticationManager; // 直接注入
	private final RedisUtil redisUtil;
	private final JwtUtil jwtUtil;
	private final LoginAttemptService loginAttemptService;

	/**
	 * 校验用户登录凭证，登录成功后生成访问令牌与刷新令牌。
	 *
	 * @param userLoginRequest 登录请求参数，包含用户名和密码
	 * @param request HTTP 请求对象，用于获取客户端 IP
	 * @return 包含令牌信息或错误信息的响应结果
	 */
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
