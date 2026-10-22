package com.findus.backend.inquiry.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/** 본문·이메일은 되돌려주지 않습니다. */
public record InquiryCreatedResponse(
		@Schema(description = "문의 ID (UUID)") UUID id,
		@Schema(description = "접수 시간 (UTC)") LocalDateTime createdAt) {}
