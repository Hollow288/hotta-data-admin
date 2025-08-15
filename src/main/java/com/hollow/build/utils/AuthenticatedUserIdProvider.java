package com.hollow.build.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;


@Component
public class AuthenticatedUserIdProvider {
	

	public Long getUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication != null && !Objects.isNull(authentication.getPrincipal())) {

            return authentication.getPrincipal() instanceof Long
                    ? (Long) authentication.getPrincipal()
                    : Long.valueOf(authentication.getPrincipal().toString());
		}

		return null;
	}
	

	public boolean isAvailable() {
		final var authentication = SecurityContextHolder.getContext().getAuthentication();
		return Optional.ofNullable(authentication).isPresent();
	}

}