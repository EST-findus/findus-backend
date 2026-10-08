package com.findus.backend.auth.dto;

/** Access Token만 본문으로 반환합니다. Refresh Token은 HttpOnly 쿠키에 담습니다. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
	@Override
	public String toString() { return "TokenResponse[내용 생략]"; }
}
