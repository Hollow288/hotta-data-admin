package com.hollow.build.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@Schema(title = "TokenSuccessResponse", accessMode = Schema.AccessMode.READ_ONLY)
public class TokenSuccessResponseDto implements Serializable {

	private String accessToken;

	private String refreshToken;

}