package com.findus.backend.news.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.findus.backend.news.entity.NewsArticle;
import com.findus.backend.news.entity.NewsPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class NewsArticleRepositoryImpl implements NewsArticleRepository {
    private final NewsArticleJpaRepository jpa;
    public NewsArticleRepositoryImpl(NewsArticleJpaRepository jpa) { this.jpa=jpa; }
    @Override public NewsArticle save(NewsArticle article) { return jpa.saveAndFlush(article); }
    @Override public boolean existsActive(String originalUrl) { return jpa.existsByOriginalUrlAndDeletedAtIsNull(originalUrl); }
    @Override public Optional<NewsArticle> findActive(UUID id) { return jpa.findByIdAndDeletedAtIsNull(id); }
    @Override public Page<NewsArticle> findActive(Pageable page) { return jpa.findAllByDeletedAtIsNull(page); }
    @Override public List<NewsArticle> findPublic(NewsPlacement placement,LocalDateTime now,int limit) {
        return jpa.findPublic(placement.visibleWith(),now,PageRequest.of(0,limit));
    }
}
