package com.hollow.build.filter;


import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.OpenApiConfiguration;
import com.hollow.build.service.RateLimitingService;
import com.hollow.build.utils.ApiEndpointSecurityInspector;
import com.hollow.build.utils.AuthenticatedUserIdProvider;
import com.hollow.build.utils.LoginAttemptService;
import com.hollow.build.utils.PathMatcherUtils;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.concurrent.TimeUnit;


/**
 * 请求限流过滤器，基于 API Key 对非安全接口进行速率限制。
 * <p>使用令牌桶算法控制请求频率，超出限制时返回 429 错误响应。</p>
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

	private final LoginAttemptService loginAttemptService;
	private final RateLimitingService rateLimitingService;
	private final RequestMappingHandlerMapping requestHandlerMapping;
	private final AuthenticatedUserIdProvider authenticatedUserIdProvider;
	private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;
	private final PathMatcherUtils pathMatcherUtils;

	/**
	 * 执行限流过滤逻辑。
	 * <p>对公开接口（非 Swagger 路径）根据 API Key 进行限流检查，
	 * 若无 API Key 则返回认证错误，超出速率限制则返回限流错误。</p>
	 *
	 * @param request     HTTP 请求对象
	 * @param response    HTTP 响应对象
	 * @param filterChain 过滤器链
	 */
	@Override
	@SneakyThrows
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
		final var unsecuredApiBeingInvoked = apiEndpointSecurityInspector.isUnsecureRequest(request);

		if (unsecuredApiBeingInvoked && !pathMatcherUtils.matchAny(OpenApiConfiguration.SWAGGER_V3_PATHS, request.getRequestURI())) {
			final var isRequestBypassed = isBypassed(request);

			if (!isRequestBypassed) {
				// 不再根据用户ID进行限流
				//	final var id = authenticatedUserIdProvider.getUserId();

				String apiKey = request.getHeader("X-API-KEY");

				if (apiKey == null || apiKey.isBlank()) {
					// 没有提供 API Key，直接返回 401
					loginAttemptService.returnTokenError(response);
					return;
				}

				final var bucket = rateLimitingService.getBucket(apiKey);
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


	/**
	 * 判断当前请求处理方法是否标记了跳过限流注解。
	 *
	 * @param request HTTP 请求对象
	 * @return 已声明 {@link BypassRateLimit} 返回 true，否则返回 false
	 */
	@SneakyThrows
	private boolean isBypassed(HttpServletRequest request) {
		var handlerChain = requestHandlerMapping.getHandler(request);
		if (handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod handlerMethod) {
			return handlerMethod.getMethod().isAnnotationPresent(BypassRateLimit.class);
		}
		return Boolean.FALSE;
	}

	/**
	 * 写入限流失败响应，并返回客户端下一次允许重试的等待时间。
	 *
	 * @param response HTTP 响应对象
	 * @param consumptionProbe 令牌桶消费结果
	 */
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
