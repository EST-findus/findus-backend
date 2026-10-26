package com.findus.backend.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface ValidPassword {
	String message() default "비밀번호는 공백만으로 구성할 수 없으며 8자 이상, UTF-8 기준 72바이트 이하여야 합니다.";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
