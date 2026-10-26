package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonImage;
import com.findus.backend.missing.dto.MissingImageMetadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MissingPersonImageRepositoryImpl implements MissingPersonImageRepository {
    private final MissingPersonImageJpaRepository jpa;
    public MissingPersonImageRepositoryImpl(MissingPersonImageJpaRepository jpa) { this.jpa=jpa; }
    @Override public List<MissingPersonImage> findForSync(UUID id) { return jpa.findAllByMissingPersonId(id); }
    @Override public void saveAll(List<MissingPersonImage> images) { jpa.saveAllAndFlush(images); }
    @Override public List<MissingImageMetadata> findMetadata(List<UUID> ids) { return ids.isEmpty()?List.of():jpa.findMetadata(ids); }
    @Override public Optional<MissingPersonImage> findPublic(UUID personId,UUID imageId) { return jpa.findPublic(personId,imageId); }
}
