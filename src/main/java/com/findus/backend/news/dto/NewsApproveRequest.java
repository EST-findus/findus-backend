package com.findus.backend.news.dto;

import java.time.OffsetDateTime;
import com.findus.backend.news.entity.NewsPlacement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description="네이버 검색 결과 중 승인할 기사. title·originalUrl·naverUrl·pubDate는 검색 결과 값을 그대로 보냅니다.")
public record NewsApproveRequest(
        @Schema(description="네이버 title 그대로. 서버가 <b> 태그·HTML 엔티티를 제거합니다") @NotBlank @Size(max=2000) String title,
        @Schema(description="네이버 originallink. 비어 있으면 link") @NotBlank @Size(max=2048) String originalUrl,
        @Schema(description="네이버 link") @Size(max=2048) String naverUrl,
        @Schema(description="네이버 pubDate (RFC 1123)",example="Thu, 22 Oct 2026 09:30:00 +0900") @Size(max=100) String pubDate,
        @Schema(description="노출 위치, 생략하면 BOTH") NewsPlacement placement,
        @Schema(description="게시 종료 시각(시간대 포함), 생략하면 계속 노출") OffsetDateTime expiresAt,
        @Schema(description="승인 메모(공개 API에는 노출하지 않음)") @Size(max=2000) String note) {}
