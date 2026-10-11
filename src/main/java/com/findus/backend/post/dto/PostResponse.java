package com.findus.backend.post.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.post.entity.Post;

public record PostResponse(UUID id, String title, String content, PostAuthorResponse author,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	public static PostResponse from(Post post) {
		return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
				PostAuthorResponse.from(post.getAuthor()), post.getCreatedAt(), post.getUpdatedAt());
	}
}
