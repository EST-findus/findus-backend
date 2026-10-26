package com.findus.backend.post.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/** 프론트에 전달하는 페이지 응답 형식을 고정합니다. 첫 페이지는 0입니다. */
public record PostPageResponse(
		@Schema(description = "현재 페이지의 게시글 목록") List<PostSummaryResponse> content,
		@Schema(description = "현재 페이지 번호: 첫 페이지는 0", example = "0") int page,
		@Schema(description = "페이지당 게시글 수: 기본 10, 최대 100", example = "10") int size,
		@Schema(description = "조회 가능한 전체 게시글 수") long totalElements,
		@Schema(description = "전체 페이지 수") int totalPages,
		@Schema(description = "다음 페이지가 있는지 여부") boolean hasNext,
		@Schema(description = "이전 페이지가 있는지 여부") boolean hasPrevious) {
	public static PostPageResponse from(Page<PostSummaryResponse> result) {
		return new PostPageResponse(result.getContent(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
	}
}
