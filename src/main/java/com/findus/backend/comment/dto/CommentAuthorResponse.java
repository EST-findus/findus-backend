package com.findus.backend.comment.dto;

import java.util.UUID;
import com.findus.backend.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommentAuthorResponse(
		@Schema(description = "작성자 회원 ID (UUID)") UUID id,
		@Schema(description = "작성자 닉네임: 탈퇴 시 탈퇴한 회원으로 표시") String nickname) {
	public static CommentAuthorResponse from(Member member) {
		return new CommentAuthorResponse(member.getId(), member.isDeleted() ? "탈퇴한 회원" : member.getNickname());
	}
}
