package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonImage;
import com.findus.backend.missing.dto.MissingImageMetadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissingPersonImageRepository {
    List<MissingPersonImage> findForSync(UUID personId);
    void saveAll(List<MissingPersonImage> images);
    List<MissingImageMetadata> findMetadata(List<UUID> personIds);
    Optional<MissingPersonImage> findPublic(UUID personId, UUID imageId);
}
