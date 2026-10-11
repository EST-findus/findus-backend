package com.findus.backend.post.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 프론트에 전달하는 페이지 응답 형식을 고정합니다. 첫 페이지는 0입니다. */
public record PostPageResponse(List<PostSummaryResponse> content, int page, int size,
		long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {
	public static PostPageResponse from(Page<PostSummaryResponse> result) {
		return new PostPageResponse(result.getContent(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
	}
}
