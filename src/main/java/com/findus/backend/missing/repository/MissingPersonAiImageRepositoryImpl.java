package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingAiImageMetadata;
import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MissingPersonAiImageRepositoryImpl implements MissingPersonAiImageRepository {
    private final MissingPersonAiImageJpaRepository jpa;
    public MissingPersonAiImageRepositoryImpl(MissingPersonAiImageJpaRepository jpa) { this.jpa=jpa; }
    @Override public MissingPersonAiImage save(MissingPersonAiImage image) { return jpa.saveAndFlush(image); }
    @Override public void saveAll(List<MissingPersonAiImage> images) { jpa.saveAllAndFlush(images); }
    @Override public List<MissingPersonAiImage> findForManagement(UUID id) {
        return jpa.findAllByMissingPersonIdOrderByCreatedAtAscIdAsc(id);
    }
    @Override public Optional<MissingAiImageMetadata> findPublicMain(UUID personId) { return jpa.findPublicMain(personId); }
    @Override public Optional<MissingImageContent> findPublicMainContent(UUID personId) { return jpa.findPublicMainContent(personId); }
    @Override public Set<UUID> findPersonIdsWithPublicMain(List<UUID> personIds) {
        return personIds.isEmpty() ? Set.of() : Set.copyOf(jpa.findPersonIdsWithPublicMain(personIds));
    }
    @Override public List<MissingAiImageMetadata> findPublicMains() { return jpa.findPublicMains(); }
    @Override public Optional<MissingPersonAiImage> findActive(UUID id) { return jpa.findActive(id); }
}
