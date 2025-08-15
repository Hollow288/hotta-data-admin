package com.hollow.build.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.utils.ApiEndpointSecurityInspector;
import com.hollow.build.utils.JwtUtil;
import com.hollow.build.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.List;
import java.util.Objects;


@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    @SneakyThrows
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        final var unsecuredApiBeingInvoked = apiEndpointSecurityInspector.isUnsecureRequest(request);

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
                    this.returnTokenError(response);
                    return;
                }

                String redisKey = "access_token:" + userid;
                Object result = redisUtil.get(redisKey);

                if(Objects.isNull(result)){
                    //throw new RuntimeException("用户未登录或AccessToken过期");
                    this.returnTokenError(response);
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
                returnTokenError(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }


    public void  returnTokenError(HttpServletResponse response) throws IOException {
        ApiResponse<Object> result = new ApiResponse<>(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write(JSONObject.toJSONString(result));
    }

}