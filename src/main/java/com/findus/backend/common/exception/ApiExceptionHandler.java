package com.findus.backend.common.exception;

import com.findus.backend.member.exception.DuplicateEmailException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> invalidRequest(MethodArgumentNotValidException exception) {
		var errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		return ResponseEntity.badRequest().body(new ApiErrorResponse(
				"INVALID_REQUEST", "입력 항목을 확인해 주세요.", errors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> unreadableRequest() {
		return ResponseEntity.badRequest().body(ApiErrorResponse.of(
				"INVALID_REQUEST", "요청 본문의 JSON 형식을 확인해 주세요."));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiErrorResponse> duplicateEmail() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
				"DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> databaseConstraint(DataIntegrityViolationException exception) {
		// 다른 DB 오류를 이메일 중복으로 오인하지 않도록 제약조건 이름까지 확인합니다.
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException constraint
					&& "uk_members_email".equals(constraint.getConstraintName())) {
				return duplicateEmail();
			}
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
				"INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다."));
	}
}
