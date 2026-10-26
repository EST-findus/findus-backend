package com.findus.backend.common.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	@Bean
	public DateTimeProvider auditingDateTimeProvider() {
		// DB 컬럼(TIMESTAMP(6))과 같은 마이크로초로 맞춰 저장 전 응답과 다시 읽은 값이 달라지지 않게 합니다.
		return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
	}
}
