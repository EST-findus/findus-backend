package com.findus.backend.comment.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommentResponse(
		@Schema(description = "댓글 ID (UUID)") UUID id,
		@Schema(description = "댓글이 속한 게시글 ID (UUID)") UUID postId,
		@Schema(description = "댓글 본문") String content,
		@Schema(description = "작성자의 공개 정보") CommentAuthorResponse author,
		@Schema(description = "작성 시간 (UTC)") LocalDateTime createdAt,
		@Schema(description = "수정 시간 (UTC)") LocalDateTime updatedAt) {
	public static CommentResponse from(Comment comment) {
		return new CommentResponse(comment.getId(), comment.getPost().getId(), comment.getContent(),
				CommentAuthorResponse.from(comment.getAuthor()), comment.getCreatedAt(), comment.getUpdatedAt());
	}
}
