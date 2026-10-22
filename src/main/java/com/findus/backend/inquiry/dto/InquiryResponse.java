package com.findus.backend.inquiry.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.inquiry.entity.Inquiry;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 전용 응답입니다. */
public record InquiryResponse(
		@Schema(description = "문의 ID (UUID)") UUID id,
		@Schema(description = "문의 유형 코드") InquiryType type,
		@Schema(description = "처리 상태") InquiryStatus status,
		@Schema(description = "문의 본문") String message,
		@Schema(description = "답변 받을 이메일. 답변 완료·종료 후에는 null") String email,
		@Schema(description = "문의를 보낸 화면 경로") String page,
		@Schema(description = "보낸 회원 ID. 비회원이면 null") UUID memberId,
		@Schema(description = "관리자 내부 메모") String adminMemo,
		@Schema(description = "처음 답변 완료·종료된 시간 (UTC)") LocalDateTime answeredAt,
		@Schema(description = "접수 시간 (UTC)") LocalDateTime createdAt,
		@Schema(description = "수정 시간 (UTC)") LocalDateTime updatedAt) {
	public static InquiryResponse from(Inquiry inquiry) {
		return new InquiryResponse(inquiry.getId(), inquiry.getType(), inquiry.getStatus(), inquiry.getMessage(),
				inquiry.getEmail(), inquiry.getPage(), inquiry.getMemberId(), inquiry.getAdminMemo(),
				inquiry.getAnsweredAt(), inquiry.getCreatedAt(), inquiry.getUpdatedAt());
	}
}
