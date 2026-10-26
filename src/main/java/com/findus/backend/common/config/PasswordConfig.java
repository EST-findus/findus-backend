package com.findus.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 로그인 보안 설정에 앞서 회원가입에 필요한 비밀번호 해싱만 준비합니다. */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		// 기본 BCrypt 해시에 알고리즘 표시를 붙여, 이후 다른 방식으로 전환할 수 있습니다.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
