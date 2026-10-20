package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonImage;
import com.findus.backend.missing.dto.MissingImageMetadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MissingPersonImageJpaRepository extends JpaRepository<MissingPersonImage,UUID> {
    List<MissingPersonImage> findAllByMissingPersonId(UUID personId);
    @Query("select new com.findus.backend.missing.dto.MissingImageMetadata(i.id,p.id,i.sortOrder,i.contentType,i.byteSize) from MissingPersonImage i join i.missingPerson p where p.id in :ids and p.sourceAvailable=true and p.deletedAt is null and i.deletedAt is null order by p.id,i.sortOrder")
    List<MissingImageMetadata> findMetadata(@Param("ids") List<UUID> ids);
    @Query("select i from MissingPersonImage i join i.missingPerson p where i.id=:imageId and p.id=:personId and p.sourceAvailable=true and p.deletedAt is null and i.deletedAt is null")
    Optional<MissingPersonImage> findPublic(@Param("personId") UUID personId,@Param("imageId") UUID imageId);
}
