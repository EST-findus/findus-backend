package com.findus.backend.common.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

	@Override
	public boolean isValid(String password, ConstraintValidatorContext context) {
		// BCrypt의 72바이트 제한을 검사합니다. 비밀번호의 앞뒤 공백은 변경하지 않습니다.
		return password != null && !password.isBlank()
				&& password.codePointCount(0, password.length()) >= 8
				&& password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}
}
