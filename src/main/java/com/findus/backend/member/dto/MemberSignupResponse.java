package com.findus.backend.member.dto;

import java.util.UUID;

import com.findus.backend.member.entity.Member;

/** 가입 결과에 필요한 정보만 반환합니다. 비밀번호와 해시는 포함하지 않습니다. */
public record MemberSignupResponse(UUID id, String email, String name, String nickname) {

	public static MemberSignupResponse from(Member member) {
		return new MemberSignupResponse(member.getId(), member.getEmail(), member.getName(), member.getNickname());
	}
}
