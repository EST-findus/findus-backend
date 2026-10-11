package com.findus.backend.post.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.post.entity.Post;

public record PostSummaryResponse(UUID id, String title, PostAuthorResponse author,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	public static PostSummaryResponse from(Post post) {
		return new PostSummaryResponse(post.getId(), post.getTitle(), PostAuthorResponse.from(post.getAuthor()),
				post.getCreatedAt(), post.getUpdatedAt());
	}
}
