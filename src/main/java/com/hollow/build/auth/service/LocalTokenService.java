package com.hollow.build.auth.service;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.auth.dto.TokenSuccessResponseDto;
import com.hollow.build.auth.dto.UserProfileDto;
import com.hollow.build.auth.entity.User;
import com.hollow.build.auth.repository.UserMapper;
import com.hollow.build.auth.util.JwtUtil;
import com.hollow.build.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LocalTokenService {
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final UserMapper userMapper;

    public TokenSuccessResponseDto issue(User user) {
        if (user == null || !"1".equals(user.getIsEnabled())) {
            throw new IllegalArgumentException("后台用户不存在或已停用");
        }
        String userId = user.getUserId().toString();
        List<String> roles = userMapper.selectRolesByUserId(user.getUserId());
        String accessToken = jwtUtil.createJWT(userId, jwtUtil.getAccessTokenTTL(), "access");
        String refreshToken = jwtUtil.createJWT(userId, jwtUtil.getRefreshTokenTTL(), "refresh");
        redisUtil.set("access_token:" + userId, JSON.toJSONString(roles), jwtUtil.getAccessTokenTTL() / 1000);
        redisUtil.set("refresh_token:" + userId, refreshToken, jwtUtil.getRefreshTokenTTL() / 1000);
        return TokenSuccessResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(new UserProfileDto(user.getUserId(), user.getUsername(), roles))
                .build();
    }

    public TokenSuccessResponseDto refresh(String refreshToken) {
        try {
            Claims claims = jwtUtil.parseJWT(refreshToken);
            if (!"refresh".equals(claims.get("token_type", String.class))) {
                return null;
            }
            Long userId = Long.valueOf(claims.getSubject());
            if (!Objects.equals(redisUtil.get("refresh_token:" + userId), refreshToken)) {
                return null;
            }
            return issue(userMapper.selectById(userId));
        } catch (Exception ex) {
            return null;
        }
    }

    public void logout(Long userId) {
        redisUtil.removeKey("access_token:" + userId);
        redisUtil.removeKey("refresh_token:" + userId);
    }
}
