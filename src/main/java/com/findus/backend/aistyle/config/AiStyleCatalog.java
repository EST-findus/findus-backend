package com.findus.backend.aistyle.config;

import com.findus.backend.aistyle.config.AiStyleProperties.Style;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiStyleProperties.class)
public class AiStyleCatalog {
    private final AiStyleProperties properties;

    public AiStyleCatalog(AiStyleProperties properties) {
        this.properties = properties;
    }

    /** 안전Dream 성별("남자"/"여자")을 AI 서버 성별(M/F)로 바꿉니다. 알 수 없으면 스타일 대상이 아닙니다. */
    public static Optional<String> sexOf(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.strip();
        if (value.startsWith("남") || value.equalsIgnoreCase("M")) return Optional.of("M");
        if (value.startsWith("여") || value.equalsIgnoreCase("F")) return Optional.of("F");
        return Optional.empty();
    }

    public List<Style> styles(String sex) {
        return switch (sex) {
            case "M" -> properties.male();
            case "F" -> properties.female();
            default -> List.of();
        };
    }

    public AiStyleProperties properties() { return properties; }
}
