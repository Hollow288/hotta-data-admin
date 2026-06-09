package com.hollow.build.auth.config;


import com.hollow.build.auth.filter.JwtAuthenticationFilter;
import com.hollow.build.ratelimit.RateLimitFilter;
import com.hollow.build.auth.handler.CustomAuthenticationEntryPoint;
import com.hollow.build.auth.util.ApiEndpointSecurityInspector;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


/**
 * Spring Security 安全配置类，定义认证过滤链、CORS 策略及密码编码器
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

	private final RateLimitFilter rateLimitFilter;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;
	private final CustomAuthenticationEntryPoint customEntryPoint;
	
	/**
	 * 配置安全过滤链，包括 CORS、CSRF、会话管理、请求授权及过滤器顺序
	 *
	 * @param http HttpSecurity 配置对象
	 * @return 构建完成的安全过滤链
	 */
	@Bean
	@SneakyThrows
	public SecurityFilterChain configure(final HttpSecurity http)  {
		http
			.cors(corsConfigurer -> corsConfigurer.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(sessionConfigurer -> sessionConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authManager -> {
					authManager
						.requestMatchers(HttpMethod.GET, apiEndpointSecurityInspector.getPublicGetEndpoints().toArray(String[]::new)).permitAll()
						.requestMatchers(HttpMethod.POST, apiEndpointSecurityInspector.getPublicPostEndpoints().toArray(String[]::new)).permitAll()
					.anyRequest().authenticated();
				})
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
			.exceptionHandling(exceptions ->
					exceptions.authenticationEntryPoint(customEntryPoint)
			);

		return http.build();
	}
	
	/**
	 * 创建 BCrypt 密码编码器
	 *
	 * @return 密码编码器实例
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}


	/**
	 * 获取认证管理器实例
	 *
	 * @param authenticationConfiguration 认证配置对象
	 * @return 认证管理器
	 * @throws Exception 获取认证管理器时可能抛出的异常
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}
	
	/**
	 * 创建全局 CORS 配置源，统一定义允许的来源、方法与请求头。
	 *
	 * @return 注册好跨域规则的 CORS 配置源
	 */
	private CorsConfigurationSource corsConfigurationSource() {
		final var corsConfiguration = new CorsConfiguration();
		corsConfiguration.setAllowedOrigins(List.of("*"));
		corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		corsConfiguration.setAllowedHeaders(List.of("Authorization", "Origin", "Content-Type", "Accept", "X-API-KEY", "X-FUND-TOKEN"));
		corsConfiguration.setExposedHeaders(List.of(
				"Content-Type",
				"Content-Disposition",
				"X-Rate-Limit-Retry-After-Seconds",
				"X-Rate-Limit-Remaining"
		));

		final var corsConfigurationSource = new UrlBasedCorsConfigurationSource();
		corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
		return corsConfigurationSource;
	}

}
