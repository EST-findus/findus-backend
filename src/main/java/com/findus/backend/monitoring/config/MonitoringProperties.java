package com.findus.backend.monitoring.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(
		@NotBlank @Size(min = 24, message = "메트릭 수집 비밀번호는 24자 이상이어야 합니다.") String password) {
}
