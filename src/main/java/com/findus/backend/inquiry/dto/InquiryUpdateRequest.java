package com.findus.backend.inquiry.dto;

import com.findus.backend.inquiry.entity.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryUpdateRequest(
		@Schema(description = "처리 상태. RECEIVED → IN_PROGRESS → ANSWERED / CLOSED 순서이며 되돌릴 수 없습니다.", example = "ANSWERED")
		@NotNull(message = "처리 상태를 골라 주세요.") InquiryStatus status,
		@Schema(description = "관리자 내부 메모(최대 2000자). 비우면 메모를 지웁니다.", example = "다음 배포에서 수정 예정")
		@Size(max = 2000, message = "관리자 메모는 2000자까지 적을 수 있어요.") String adminMemo) {}
