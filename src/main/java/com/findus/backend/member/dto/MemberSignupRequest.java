package com.findus.backend.member.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.findus.backend.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberSignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일 형식을 입력해 주세요.")
		@Size(max = 254, message = "이메일은 254자 이하여야 합니다.") String email,

		@ValidPassword @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.") String name,

		@NotBlank(message = "닉네임을 입력해 주세요.")
		@Size(max = 50, message = "닉네임은 50자 이하여야 합니다.") String nickname) {

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
