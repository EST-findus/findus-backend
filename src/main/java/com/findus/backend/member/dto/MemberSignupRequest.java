package com.findus.backend.member.dto;

import java.util.Locale;
import com.findus.backend.common.validation.ValidPassword;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberSignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식을 입력해 주세요.")
		@Size(max = 254, message = "이메일은 254자 이하여야 합니다.") @Schema(description = "회원 이메일: 앞뒤 공백 제거 및 소문자 변환", example = "swagger.test@example.com") String email,

		@ValidPassword @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(description = "비밀번호: 공백만 사용 불가, 8자 이상, UTF-8 기준 72바이트 이하", example = "Local-test123!", minLength = 8, writeOnly = true) String password,

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.") @Schema(description = "이름: 최대 100자", example = "테스트회원") String name,

		@NotBlank(message = "닉네임을 입력해 주세요.")
		@Size(max = 50, message = "닉네임은 50자 이하여야 합니다.") @Schema(description = "닉네임: 최대 50자", example = "찾음이") String nickname) {

	public MemberSignupRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
		name = name == null ? null : name.strip();
		nickname = nickname == null ? null : nickname.strip();
	}

	@Override
	public String toString() {
		// 요청 객체를 출력하더라도 개인정보와 비밀번호가 드러나지 않게 합니다.
		return "MemberSignupRequest[내용 생략]";
	}
}
