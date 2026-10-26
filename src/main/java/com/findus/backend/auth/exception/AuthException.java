package com.findus.backend.auth.exception;

public class AuthException extends RuntimeException {
	public AuthException() {
		super("인증 정보가 올바르지 않거나 만료되었습니다.");
	}
}
