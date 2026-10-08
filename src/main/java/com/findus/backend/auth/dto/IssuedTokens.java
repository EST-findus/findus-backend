package com.findus.backend.auth.dto;

/** 서비스와 컨트롤러 사이에서만 사용하는 값입니다. API 본문으로 반환하지 않습니다. */
public record IssuedTokens(TokenResponse response, String refreshToken) {
	@Override
	public String toString() { return "IssuedTokens[내용 생략]"; }
}
