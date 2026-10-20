package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
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
}
