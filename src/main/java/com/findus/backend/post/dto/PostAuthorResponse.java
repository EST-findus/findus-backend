package com.findus.backend.post.dto;

import java.util.UUID;
import com.findus.backend.member.entity.Member;

public record PostAuthorResponse(UUID id, String nickname) {
	public static PostAuthorResponse from(Member author) {
		// 공개 게시글 응답에는 회원 이메일·비밀번호·실명을 포함하지 않습니다.
		return new PostAuthorResponse(author.getId(), author.isDeleted() ? "탈퇴한 회원" : author.getNickname());
	}
}
