package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.TokenSuccessResponseDto;
import com.hollow.build.dto.UserLoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;

public interface UserService {

    ApiResponse<TokenSuccessResponseDto> login(UserLoginRequestDto userLoginRequest, HttpServletRequest request);
}
