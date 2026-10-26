package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiStyleImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MissingPersonAiStyleImageRepositoryImpl implements MissingPersonAiStyleImageRepository {
    private final MissingPersonAiStyleImageJpaRepository jpa;
    public MissingPersonAiStyleImageRepositoryImpl(MissingPersonAiStyleImageJpaRepository jpa) { this.jpa=jpa; }
    @Override public MissingPersonAiStyleImage save(MissingPersonAiStyleImage image) { return jpa.saveAndFlush(image); }
    @Override public List<String> findActiveKeys(UUID aiImageId) { return jpa.findActiveKeys(aiImageId); }
    @Override public Optional<MissingImageContent> findPublicContent(UUID personId,String styleKey) {
        return jpa.findPublicContent(personId,styleKey);
    }
}
