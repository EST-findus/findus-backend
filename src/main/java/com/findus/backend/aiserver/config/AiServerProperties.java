package com.findus.backend.aiserver.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 서버 주소와 키입니다. 머리 변환은 GPU 대기를 포함해 수십 초가 걸릴 수 있습니다. */
@ConfigurationProperties("app.ai-server")
public record AiServerProperties(URI baseUrl, String apiKey, Duration timeout) {
    public AiServerProperties {
        if (baseUrl == null) baseUrl = URI.create("http://127.0.0.1:8000");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofMinutes(5);
        if (apiKey != null && apiKey.isBlank()) apiKey = null;
    }
}
