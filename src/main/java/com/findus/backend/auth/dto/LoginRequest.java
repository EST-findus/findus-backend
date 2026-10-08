package com.findus.backend.auth.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.findus.backend.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식을 입력해 주세요.")
		@Size(max = 254, message = "이메일은 254자 이하여야 합니다.") String email,
		@ValidPassword @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
	public LoginRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	public String toString() { return "LoginRequest[내용 생략]"; }
}
