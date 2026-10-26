package com.findus.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/** Access Token만 본문으로 반환합니다. Refresh Token은 HttpOnly 쿠키에 담습니다. */
public record TokenResponse(
		@Schema(description = "API 인증에 사용할 Access Token") String accessToken,
		@Schema(description = "인증 방식", example = "Bearer") String tokenType,
		@Schema(description = "Access Token 유효 기간 (초)", example = "900") long expiresIn) {
	@Override
	public String toString() { return "TokenResponse[내용 생략]"; }
}
