package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
import java.util.UUID;

public interface MissingPersonAiImageRepository {
    MissingPersonAiImage save(MissingPersonAiImage image);
    void saveAll(List<MissingPersonAiImage> images);
    List<MissingPersonAiImage> findForManagement(UUID personId);
}
