package com.findus.backend.inquiry.exception;

import java.util.List;
import com.findus.backend.common.exception.ApiErrorResponse;
import org.springframework.http.HttpStatus;

/** 상태 코드와 화면에 그대로 보여줄 안내를 담습니다. 문의 본문·이메일은 넣지 않습니다. */
public class InquiryException extends RuntimeException {
	private final HttpStatus status;
	private final String code;
	private final List<ApiErrorResponse.FieldError> errors;

	private InquiryException(HttpStatus status, String code, String message, List<ApiErrorResponse.FieldError> errors) {
		super(message);
		this.status = status;
		this.code = code;
		this.errors = errors;
	}

	public HttpStatus status() { return status; }
	public String code() { return code; }
	public List<ApiErrorResponse.FieldError> errors() { return errors; }

	/** 첫 번째 항목 문구를 message로 올려 화면이 한 줄로 바로 보여줄 수 있게 합니다. */
	public static InquiryException invalid(List<ApiErrorResponse.FieldError> errors) {
		return new InquiryException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", errors.get(0).message(), errors);
	}

	public static InquiryException invalidPage() {
		return new InquiryException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "문의 페이지 또는 정렬 조건을 확인해 주세요.", List.of());
	}

	public static InquiryException notFound() {
		return new InquiryException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다.", List.of());
	}

	public static InquiryException statusConflict() {
		return new InquiryException(HttpStatus.CONFLICT, "INQUIRY_STATUS_CONFLICT", "이전 처리 상태로 되돌릴 수 없습니다.", List.of());
	}

	public static InquiryException tooManyRequests() {
		return new InquiryException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "문의가 너무 많아요. 잠시 후 다시 시도해 주세요.", List.of());
	}
}
