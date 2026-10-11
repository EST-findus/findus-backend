package com.findus.backend.post.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

public record PostResponse(
		@Schema(description = "게시글 ID (UUID)") UUID id,
		@Schema(description = "제목") String title,
		@Schema(description = "본문") String content,
		@Schema(description = "작성자 공개 정보") PostAuthorResponse author,
		@Schema(description = "작성 시간 (UTC)") LocalDateTime createdAt,
		@Schema(description = "수정 시간 (UTC)") LocalDateTime updatedAt) {
	public static PostResponse from(Post post) {
		return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
				PostAuthorResponse.from(post.getAuthor()), post.getCreatedAt(), post.getUpdatedAt());
	}
}
