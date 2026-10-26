package com.findus.backend.news.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자가 승인한 기사입니다. 행이 존재하면 승인된 것이고, 소프트 삭제는 노출 중단입니다. */
@Getter
@Entity
@Table(name="news_articles",schema="findus")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=600) private String title;
    @Column(name="original_url",nullable=false,length=2048,updatable=false) private String originalUrl;
    @Column(name="naver_url",length=2048) private String naverUrl;
    @Column(name="published_at") private LocalDateTime publishedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private NewsPlacement placement;
    @Column(name="expires_at") private LocalDateTime expiresAt;
    @Column(name="approved_by",nullable=false,updatable=false) private UUID approvedBy;
    @Column(length=2000) private String note;

    public NewsArticle(String title,String originalUrl,String naverUrl,LocalDateTime publishedAt,
            NewsPlacement placement,LocalDateTime expiresAt,UUID approvedBy,String note) {
        this.title=title;this.originalUrl=originalUrl;this.naverUrl=naverUrl;this.publishedAt=publishedAt;
        this.placement=placement;this.expiresAt=expiresAt;this.approvedBy=approvedBy;this.note=note;
    }
}
