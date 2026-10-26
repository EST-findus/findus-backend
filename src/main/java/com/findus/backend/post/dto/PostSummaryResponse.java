package com.findus.backend.post.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

public record PostSummaryResponse(
		@Schema(description = "게시글 ID (UUID)") UUID id,
		@Schema(description = "제목") String title,
		@Schema(description = "작성자 공개 정보") PostAuthorResponse author,
		@Schema(description = "작성 시간 (UTC)") LocalDateTime createdAt,
		@Schema(description = "수정 시간 (UTC)") LocalDateTime updatedAt) {
	public static PostSummaryResponse from(Post post) {
		return new PostSummaryResponse(post.getId(), post.getTitle(), PostAuthorResponse.from(post.getAuthor()),
				post.getCreatedAt(), post.getUpdatedAt());
	}
}
