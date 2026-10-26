package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingAiImageMetadata;
import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MissingPersonAiImageJpaRepository extends JpaRepository<MissingPersonAiImage,UUID> {
    String PUBLIC_MAIN="from MissingPersonAiImage a join a.missingPerson p where p.sourceAvailable=true and p.deletedAt is null and a.main=true and a.deletedAt is null";
    List<MissingPersonAiImage> findAllByMissingPersonIdOrderByCreatedAtAscIdAsc(UUID personId);
    @Query("select new com.findus.backend.missing.dto.MissingAiImageMetadata(a.id,p.id,p.sex,a.targetAge,a.createdAt) "+PUBLIC_MAIN+" and p.id=:personId")
    Optional<MissingAiImageMetadata> findPublicMain(@Param("personId") UUID personId);
    @Query("select new com.findus.backend.missing.dto.MissingImageContent(a.contentType,a.content) "+PUBLIC_MAIN+" and p.id=:personId")
    Optional<MissingImageContent> findPublicMainContent(@Param("personId") UUID personId);
    @Query("select p.id "+PUBLIC_MAIN+" and p.id in :ids")
    List<UUID> findPersonIdsWithPublicMain(@Param("ids") List<UUID> ids);
    @Query("select new com.findus.backend.missing.dto.MissingAiImageMetadata(a.id,p.id,p.sex,a.targetAge,a.createdAt) "+PUBLIC_MAIN+" order by p.id")
    List<MissingAiImageMetadata> findPublicMains();
    @Query("select a from MissingPersonAiImage a join fetch a.missingPerson where a.id=:id and a.deletedAt is null")
    Optional<MissingPersonAiImage> findActive(@Param("id") UUID id);
}
