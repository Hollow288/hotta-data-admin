package com.hollow.build.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;


/**
 * 已认证用户 ID 提供器
 * <p>
 * 从 Spring Security 上下文中获取当前已认证用户的 ID 信息。
 * </p>
 */
@Component
public class AuthenticatedUserIdProvider {
	

	/**
	 * 获取当前已认证用户的 ID
	 *
	 * @return 用户 ID；如果用户未认证或 principal 为空则返回 null
	 */
	public Long getUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication != null && !Objects.isNull(authentication.getPrincipal())) {

            return authentication.getPrincipal() instanceof Long
                    ? (Long) authentication.getPrincipal()
                    : Long.valueOf(authentication.getPrincipal().toString());
		}

		return null;
	}
	

	/**
	 * 判断当前安全上下文中是否存在认证信息
	 *
	 * @return 存在认证信息返回 true，否则返回 false
	 */
	public boolean isAvailable() {
		final var authentication = SecurityContextHolder.getContext().getAuthentication();
		return Optional.ofNullable(authentication).isPresent();
	}

}