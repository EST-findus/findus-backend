package com.findus.backend.common.exception;

import com.findus.backend.member.exception.DuplicateEmailException;
import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.post.exception.PostForbiddenException;
import com.findus.backend.post.exception.PostNotFoundException;
import com.findus.backend.post.exception.PostPageException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
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

	@ExceptionHandler(PostNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> postNotFound() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
				"POST_NOT_FOUND", "게시글을 찾을 수 없습니다."));
	}

	@ExceptionHandler(PostForbiddenException.class)
	public ResponseEntity<ApiErrorResponse> postForbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(
				"FORBIDDEN", "작성자만 게시글을 수정하거나 삭제할 수 있습니다."));
	}

	@ExceptionHandler(PostPageException.class)
	public ResponseEntity<ApiErrorResponse> invalidPage() {
		return ResponseEntity.badRequest().body(ApiErrorResponse.of(
				"INVALID_REQUEST", "페이지 또는 정렬 조건을 확인해 주세요."));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> invalidParameter() {
		return ResponseEntity.badRequest().body(ApiErrorResponse.of(
				"INVALID_REQUEST", "요청 주소와 매개변수 형식을 확인해 주세요."));
	}

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiErrorResponse> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(
				"UNAUTHORIZED", "인증 정보가 올바르지 않거나 만료되었습니다."));
	}

	@ExceptionHandler(DataAccessResourceFailureException.class)
	public ResponseEntity<ApiErrorResponse> unavailable() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiErrorResponse.of(
				"SERVICE_UNAVAILABLE", "서비스를 잠시 사용할 수 없습니다."));
	}

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
