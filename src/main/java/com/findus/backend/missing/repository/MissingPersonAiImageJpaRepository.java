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
    String PUBLIC="from MissingPersonAiImage a join a.missingPerson p where p.sourceAvailable=true and p.deletedAt is null and a.deletedAt is null";
    String PUBLIC_MAIN=PUBLIC+" and a.main=true";
    String METADATA="select new com.findus.backend.missing.dto.MissingAiImageMetadata(a.id,p.id,p.sex,a.targetAge,a.createdAt,a.main) ";
    List<MissingPersonAiImage> findAllByMissingPersonIdOrderByCreatedAtAscIdAsc(UUID personId);
    @Query(METADATA+PUBLIC_MAIN+" and p.id=:personId")
    Optional<MissingAiImageMetadata> findPublicMain(@Param("personId") UUID personId);
    @Query(METADATA+PUBLIC+" and p.id=:personId order by a.main desc, a.createdAt asc, a.id asc")
    List<MissingAiImageMetadata> findPublicAll(@Param("personId") UUID personId);
    @Query("select new com.findus.backend.missing.dto.MissingImageContent(a.contentType,a.content) "+PUBLIC+" and p.id=:personId and a.id=:aiImageId")
    Optional<MissingImageContent> findPublicContent(@Param("personId") UUID personId,@Param("aiImageId") UUID aiImageId);
    @Query("select new com.findus.backend.missing.dto.MissingImageContent(a.contentType,a.content) "+PUBLIC+" and p.id=:personId "
            +"order by a.main desc, a.createdAt asc, a.id asc limit 1")
    Optional<MissingImageContent> findPublicFirstContent(@Param("personId") UUID personId);
    @Query("select distinct p.id "+PUBLIC+" and p.id in :ids")
    List<UUID> findPersonIdsWithPublicAi(@Param("ids") List<UUID> ids);
    @Query(METADATA+PUBLIC_MAIN+" order by p.id")
    List<MissingAiImageMetadata> findPublicMains();
    @Query("select a from MissingPersonAiImage a join fetch a.missingPerson where a.id=:id and a.deletedAt is null")
    Optional<MissingPersonAiImage> findActive(@Param("id") UUID id);
}
