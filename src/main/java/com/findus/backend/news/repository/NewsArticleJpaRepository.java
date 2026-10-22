package com.findus.backend.news.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.findus.backend.news.entity.NewsArticle;
import com.findus.backend.news.entity.NewsPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NewsArticleJpaRepository extends JpaRepository<NewsArticle,UUID> {
    boolean existsByOriginalUrlAndDeletedAtIsNull(String originalUrl);
    Optional<NewsArticle> findByIdAndDeletedAtIsNull(UUID id);
    Page<NewsArticle> findAllByDeletedAtIsNull(Pageable pageable);
    @Query("select a from NewsArticle a where a.deletedAt is null and (a.expiresAt is null or a.expiresAt > :now) and a.placement in :placements order by a.createdAt desc, a.id desc")
    List<NewsArticle> findPublic(@Param("placements") Collection<NewsPlacement> placements,@Param("now") LocalDateTime now,Pageable pageable);
}
