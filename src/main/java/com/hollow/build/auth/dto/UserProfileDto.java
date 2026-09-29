package com.hollow.build.auth.dto;

import java.util.List;

public record UserProfileDto(Long userId, String username, List<String> roles) {
}
