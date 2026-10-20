package com.findus.backend.missing.repository;

import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MissingPersonAiImageJpaRepository extends JpaRepository<MissingPersonAiImage,UUID> {
    List<MissingPersonAiImage> findAllByMissingPersonIdOrderByCreatedAtAscIdAsc(UUID personId);
}
