package com.hollow.build.filter;


import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.service.RateLimitingService;
import com.hollow.build.utils.ApiEndpointSecurityInspector;
import com.hollow.build.utils.AuthenticatedUserIdProvider;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.concurrent.TimeUnit;


@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitingService rateLimitingService;
	private final RequestMappingHandlerMapping requestHandlerMapping;
	private final AuthenticatedUserIdProvider authenticatedUserIdProvider;
	private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;


	@Override
	@SneakyThrows
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
		final var unsecuredApiBeingInvoked = apiEndpointSecurityInspector.isUnsecureRequest(request);

		if (!unsecuredApiBeingInvoked && authenticatedUserIdProvider.isAvailable()) {
			final var isRequestBypassed = isBypassed(request);

			if (!isRequestBypassed) {
				final var userId = authenticatedUserIdProvider.getUserId();
				final var bucket = rateLimitingService.getBucket(userId);
				final var consumptionProbe = bucket.tryConsumeAndReturnRemaining(1);
				final var isConsumptionPassed = consumptionProbe.isConsumed();

				if (!isConsumptionPassed) {
					setRateLimitErrorDetails(response, consumptionProbe);
					return;
				}

				final var remainingTokens = consumptionProbe.getRemainingTokens();
				response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
			}
		}
		filterChain.doFilter(request, response);
	}


	@SneakyThrows
	private boolean isBypassed(HttpServletRequest request) {
		var handlerChain = requestHandlerMapping.getHandler(request);
		if (handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod handlerMethod) {
			return handlerMethod.getMethod().isAnnotationPresent(BypassRateLimit.class);
		}
		return Boolean.FALSE;
	}

	@SneakyThrows
	private void setRateLimitErrorDetails(HttpServletResponse response, final ConsumptionProbe consumptionProbe) {
		ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getCode(), GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getMsg());
		response.setStatus(200);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("utf-8");
		final var waitPeriod = TimeUnit.NANOSECONDS.toSeconds(consumptionProbe.getNanosToWaitForRefill());
		response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitPeriod));
		response.getWriter().write(JSONObject.toJSONString(result));
	}


}