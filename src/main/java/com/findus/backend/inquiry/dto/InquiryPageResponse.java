package com.findus.backend.inquiry.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

public record InquiryPageResponse(
		@Schema(description = "현재 페이지의 문의 목록") List<InquiryResponse> content,
		@Schema(description = "현재 페이지 번호: 첫 페이지는 0", example = "0") int page,
		@Schema(description = "페이지당 문의 수: 기본 20, 최대 100", example = "20") int size,
		@Schema(description = "조회 가능한 전체 문의 수") long totalElements,
		@Schema(description = "전체 페이지 수") int totalPages,
		@Schema(description = "다음 페이지 여부") boolean hasNext,
		@Schema(description = "이전 페이지 여부") boolean hasPrevious) {
	public static InquiryPageResponse from(Page<InquiryResponse> page) {
		return new InquiryPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.hasNext(), page.hasPrevious());
	}
}
