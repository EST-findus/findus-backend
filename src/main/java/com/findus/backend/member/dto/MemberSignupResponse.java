package com.findus.backend.member.dto;

import java.util.UUID;
import com.findus.backend.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

/** 가입 결과에 필요한 정보만 반환합니다. 비밀번호와 해시는 포함하지 않습니다. */
public record MemberSignupResponse(
		@Schema(description = "생성된 회원 ID (UUID)") UUID id,
		@Schema(description = "가입 이메일") String email,
		@Schema(description = "이름") String name,
		@Schema(description = "닉네임") String nickname) {

	public static MemberSignupResponse from(Member member) {
		return new MemberSignupResponse(member.getId(), member.getEmail(), member.getName(), member.getNickname());
	}
}
