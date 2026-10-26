package com.findus.backend.news.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.findus.backend.news.entity.NewsArticle;
import com.findus.backend.news.entity.NewsPlacement;

/** 시각은 모두 UTC입니다. note는 관리자 응답에만 포함합니다. */
public record NewsArticleResponse(UUID id,String title,String originalUrl,String naverUrl,Instant publishedAt,
        NewsPlacement placement,Instant expiresAt,@JsonInclude(JsonInclude.Include.NON_NULL) String note,Instant approvedAt) {
    public static NewsArticleResponse admin(NewsArticle a) { return of(a,a.getNote()); }
    public static NewsArticleResponse open(NewsArticle a) { return of(a,null); }
    private static NewsArticleResponse of(NewsArticle a,String note) {
        return new NewsArticleResponse(a.getId(),a.getTitle(),a.getOriginalUrl(),a.getNaverUrl(),utc(a.getPublishedAt()),
                a.getPlacement(),utc(a.getExpiresAt()),note,utc(a.getCreatedAt()));
    }
    private static Instant utc(LocalDateTime time) { return time==null ? null : time.toInstant(ZoneOffset.UTC); }
}
