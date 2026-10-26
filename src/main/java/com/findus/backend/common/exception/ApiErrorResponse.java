package com.findus.backend.common.exception;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** 입력한 값은 포함하지 않고, 어느 항목을 고쳐야 하는지만 알려줍니다. */
public record ApiErrorResponse(
		@Schema(description = "오류 코드", example = "INVALID_REQUEST") String code,
		@Schema(description = "오류 안내") String message,
		@Schema(description = "입력 항목별 오류 목록") List<FieldError> errors) {

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(code, message, List.of());
	}

	public record FieldError(
		@Schema(description = "수정할 입력 항목") String field, String message) {}
}
