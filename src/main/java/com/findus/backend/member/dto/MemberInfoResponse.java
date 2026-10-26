package com.findus.backend.member.dto;

import java.util.UUID;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.entity.MemberRole;
import com.findus.backend.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberInfoResponse(
		@Schema(description = "회원 ID (UUID)") UUID id,
		@Schema(description = "회원 이메일") String email,
		@Schema(description = "이름") String name,
		@Schema(description = "닉네임") String nickname,
		@Schema(description = "회원 권한: USER 또는 ADMIN") MemberRole role,
		@Schema(description = "회원 상태: ACTIVE 또는 INACTIVE") MemberStatus status) {
	public static MemberInfoResponse from(Member member) {
		return new MemberInfoResponse(member.getId(), member.getEmail(), member.getName(), member.getNickname(),
				member.getRole(), member.getStatus());
	}
}
