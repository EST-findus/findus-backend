package com.findus.backend.aistyle.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 아동 성별별로 미리 만들 머리 스타일입니다. key는 AI 서버 configs/hair_presets.yaml과 같아야 합니다.
 * AI 서버가 꺼져 있어도 버튼 이름을 보여줄 수 있도록 백엔드가 목록을 가집니다.
 */
@ConfigurationProperties("app.ai-style")
public record AiStyleProperties(List<Style> male, List<Style> female, Duration busyRetryDelay, Integer busyRetries) {
    public record Style(String key, String name) {}

    public AiStyleProperties {
        male = male == null ? List.of() : List.copyOf(male);
        female = female == null ? List.of() : List.copyOf(female);
        if (busyRetryDelay == null || busyRetryDelay.isNegative()) busyRetryDelay = Duration.ofSeconds(30);
        if (busyRetries == null || busyRetries < 0) busyRetries = 3;
    }
}
