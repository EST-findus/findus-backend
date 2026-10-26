package com.findus.backend.news.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.findus.backend.news.entity.NewsArticle;
import com.findus.backend.news.entity.NewsPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NewsArticleRepository {
    NewsArticle save(NewsArticle article);
    boolean existsActive(String originalUrl);
    Optional<NewsArticle> findActive(UUID id);
    Page<NewsArticle> findActive(Pageable page);
    List<NewsArticle> findPublic(NewsPlacement placement,LocalDateTime now,int limit);
}
