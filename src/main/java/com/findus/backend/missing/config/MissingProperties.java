package com.findus.backend.missing.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.missing")
public record MissingProperties(URI baseUrl, String esntlId, String authKey, int maxPages) {
    public MissingProperties {
        if (baseUrl == null || maxPages < 1 || maxPages > 100) throw new IllegalArgumentException("실종자 API 설정을 확인하세요.");
    }
}
