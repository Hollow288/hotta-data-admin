package com.hollow.build.auth.util;

import com.hollow.build.config.OpenApiConfiguration;
import com.hollow.build.config.OpenApiConfigurationProperties;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.utils.PathMatcherUtils;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;


/**
 * API 端点安全检查器
 * <p>
 * 在应用启动时扫描所有标注了 {@link PublicEndpoint} 注解的接口，
 * 将其收集为公开端点列表，用于判断请求是否需要认证。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ApiEndpointSecurityInspector {

	private final RequestMappingHandlerMapping requestHandlerMapping;
	private final OpenApiConfigurationProperties openApiConfigurationProperties;
	private final PathMatcherUtils pathMatcherUtils;

	
	@Getter
	private List<String> publicGetEndpoints = new ArrayList<String>();
	@Getter
	private List<String> publicPostEndpoints = new ArrayList<String>();
	

	/**
	 * 初始化方法，在 Bean 创建后自动执行。
	 * 扫描所有 Controller 方法，将标注了 {@link PublicEndpoint} 的接口路径
	 * 按 HTTP 方法分类存入公开端点列表；同时根据配置决定是否放行 Swagger 路径。
	 */
	@PostConstruct
	public void init() {
		final var handlerMethods = requestHandlerMapping.getHandlerMethods();
		handlerMethods.forEach((requestInfo, handlerMethod) -> {
			if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class)) {
				final var httpMethod = requestInfo.getMethodsCondition().getMethods().iterator().next().asHttpMethod();
				final var apiPaths = requestInfo.getPathPatternsCondition().getPatternValues();

				if (httpMethod.equals(GET)) {
					publicGetEndpoints.addAll(apiPaths);
				} else if (httpMethod.equals(POST)) {
					publicPostEndpoints.addAll(apiPaths);
				}
				
			}
		});


		final var openApiEnabled = openApiConfigurationProperties.isEnabled();
		if (openApiEnabled) {
			publicGetEndpoints.addAll(OpenApiConfiguration.SWAGGER_V3_PATHS);
		}

	}


	/**
	 * 判断指定的 HTTP 请求是否为无需认证的公开请求
	 *
	 * @param request HTTP 请求对象
	 * @return 如果请求路径匹配公开端点列表则返回 true，否则返回 false
	 */
	public boolean isUnsecureRequest(@NonNull final HttpServletRequest request) {
		final var requestHttpMethod = HttpMethod.valueOf(request.getMethod());
		var unsecuredApiPaths = getUnsecuredApiPaths(requestHttpMethod);
		unsecuredApiPaths = Optional.ofNullable(unsecuredApiPaths).orElseGet(ArrayList::new);

		return pathMatcherUtils.matchAny(unsecuredApiPaths, request.getRequestURI());
	}


	/**
	 * 根据 HTTP 方法获取对应的公开端点路径列表
	 *
	 * @param httpMethod HTTP 方法（GET、POST 等）
	 * @return 对应的公开端点路径列表；不支持的方法返回空列表
	 */
	private List<String> getUnsecuredApiPaths(@NonNull final HttpMethod httpMethod) {
        return switch (httpMethod) {
            case GET -> publicGetEndpoints;
            case POST -> publicPostEndpoints;
            default -> Collections.emptyList();
        };
	}
	
}