package com.findus.backend.auth.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@NotBlank String secret, @NotBlank String issuer,
		@NotNull Duration accessTtl, @NotNull Duration refreshTtl,
		boolean cookieSecure, @NotEmpty List<String> allowedOrigins) {
	public AuthProperties {
		if (accessTtl != null && accessTtl.getSeconds() < 1
				|| refreshTtl != null && refreshTtl.getSeconds() < 1) {
			throw new IllegalArgumentException("토큰 유효기간은 1초 이상이어야 합니다.");
		}
		if (allowedOrigins != null && allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
			throw new IllegalArgumentException("허용할 프론트 주소는 와일드카드 없이 지정하세요.");
		}
	}

	@Override
	public String toString() { return "AuthProperties[비밀값 생략]"; }
}
