package com.hollow.build.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Schema(title = "UserLoginRequest", accessMode = Schema.AccessMode.WRITE_ONLY)
public class UserLoginRequestDto implements Serializable {

	@NotBlank(message = "username must not be empty")
	@Schema(requiredMode = RequiredMode.REQUIRED, example = "username", description = "username associated with user account already created in the system")
	private String username;

	@NotBlank(message = "password must not be empty")
	@Schema(requiredMode = RequiredMode.REQUIRED, example = "password", description = "password corresponding to provided email-id")
	private String password;

}
