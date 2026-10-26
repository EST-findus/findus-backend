package com.findus.backend.comment.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

public record CommentPageResponse(
		@Schema(description = "현재 페이지의 댓글 목록") List<CommentResponse> content,
		@Schema(description = "현재 페이지 번호: 첫 페이지는 0", example = "0") int page,
		@Schema(description = "페이지당 댓글 수: 기본 10, 최대 100", example = "10") int size,
		@Schema(description = "조회 가능한 전체 댓글 수") long totalElements,
		@Schema(description = "전체 페이지 수") int totalPages,
		@Schema(description = "다음 페이지 여부") boolean hasNext,
		@Schema(description = "이전 페이지 여부") boolean hasPrevious) {
	public static CommentPageResponse from(Page<CommentResponse> page) {
		return new CommentPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.hasNext(), page.hasPrevious());
	}
}
