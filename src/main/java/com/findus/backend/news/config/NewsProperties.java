package com.findus.backend.news.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 인증키가 비어 있어도 서버는 시작하고, 검색 요청만 503으로 안내합니다. endpoint는 테스트에서만 지정합니다. */
@ConfigurationProperties("app.news.naver")
public record NewsProperties(String provider,String clientId,String clientSecret,String defaultQuery,Integer display,URI endpoint) {
    public NewsProperties {
        provider=provider==null ? "hub" : provider.trim().toLowerCase();
        clientId=clientId==null ? "" : clientId.trim();
        clientSecret=clientSecret==null ? "" : clientSecret.trim();
        defaultQuery=defaultQuery==null || defaultQuery.isBlank() ? "장기 실종아동" : defaultQuery.trim();
        display=display==null ? 10 : display;
        if (!provider.equals("hub") && !provider.equals("developers")) throw new IllegalArgumentException("NAVER_API_PROVIDER는 hub 또는 developers여야 합니다.");
        if (display<1 || display>100 || defaultQuery.length()>500) throw new IllegalArgumentException("네이버 뉴스 기본 검색 설정을 확인하세요.");
    }
    public boolean configured() { return !clientId.isEmpty() && !clientSecret.isEmpty(); }
}
