package com.findus.backend.member.dto;

import java.util.UUID;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.entity.MemberRole;
import com.findus.backend.member.entity.MemberStatus;

public record MemberInfoResponse(UUID id, String email, String name, String nickname,
		MemberRole role, MemberStatus status) {
	public static MemberInfoResponse from(Member member) {
		return new MemberInfoResponse(member.getId(), member.getEmail(), member.getName(), member.getNickname(),
				member.getRole(), member.getStatus());
	}
}
