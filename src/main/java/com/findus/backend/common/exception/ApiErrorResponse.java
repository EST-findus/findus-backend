package com.findus.backend.common.exception;

import java.util.List;

/** 입력한 값은 포함하지 않고, 어느 항목을 고쳐야 하는지만 알려줍니다. */
public record ApiErrorResponse(String code, String message, List<FieldError> errors) {

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(code, message, List.of());
	}

	public record FieldError(String field, String message) {}
}
