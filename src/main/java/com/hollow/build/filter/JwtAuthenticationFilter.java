package com.hollow.build.filter;

import com.alibaba.fastjson2.JSON;

import com.hollow.build.utils.ApiEndpointSecurityInspector;
import com.hollow.build.utils.JwtUtil;
import com.hollow.build.utils.LoginAttemptService;
import com.hollow.build.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * JWT 认证过滤器，用于拦截请求并校验 JWT Token 的合法性。
 * <p>对于非公开接口，从请求头中提取 Bearer Token 并解析用户信息，
 * 将认证信息设置到 Spring Security 上下文中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;
    private final LoginAttemptService loginAttemptService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";


    /**
     * 执行 JWT 认证过滤逻辑。
     * <p>对非公开接口校验 Authorization 请求头中的 Bearer Token，
     * 解析用户身份并设置 SecurityContext。Token 无效或过期时返回错误响应。</p>
     *
     * @param request     HTTP 请求对象
     * @param response    HTTP 响应对象
     * @param filterChain 过滤器链
     */
    @Override
    @SneakyThrows
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        final var unsecuredApiBeingInvoked = apiEndpointSecurityInspector.isUnsecureRequest(request);

//        log.info("=== 请求进入过滤器 ===");
//        log.info("Request URI: {}", request.getRequestURI());
//        log.info("Request URL: {}", request.getRequestURL());
//        log.info("Request Method: {}", request.getMethod());
//        log.info("Query String: {}", request.getQueryString());
//        log.info("Context Path: {}", request.getContextPath());
//        log.info("Servlet Path: {}", request.getServletPath());
//        log.info("Path Info: {}", request.getPathInfo());
//        log.info("Authorization Header: {}", request.getHeader(AUTHORIZATION_HEADER));
//        log.info("Content-Type: {}", request.getContentType());
//        log.info("=== 结束请求信息 ===");



        if (!unsecuredApiBeingInvoked) {
            final var authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

            if (StringUtils.isNotEmpty(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX) ) {
                final var token = authorizationHeader.replace(BEARER_PREFIX, StringUtils.EMPTY);

                String userid;
                try {
                    Claims claims = jwtUtil.parseJWT(token);
                    userid = claims.getSubject();
                } catch (Exception e) {
                    e.printStackTrace();
                    loginAttemptService.returnTokenError(response);
                    return;
                }

                String redisKey = "access_token:" + userid;
                Object result = redisUtil.get(redisKey);

                if(Objects.isNull(result)){
                    //throw new RuntimeException("用户未登录或AccessToken过期");
                    loginAttemptService.returnTokenError(response);
                    return;
                }

                List<String> permissions = JSON.parseArray(result.toString(), String.class);
                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                final var authentication = new UsernamePasswordAuthenticationToken(userid, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                loginAttemptService.returnTokenError(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }


}