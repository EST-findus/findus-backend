package com.findus.backend.missing.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.missing.entity.MissingPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

interface MissingPersonJpaRepository extends JpaRepository<MissingPerson,UUID> {
    Page<MissingPerson> findAllBySourceAvailableTrueAndDeletedAtIsNull(Pageable pageable);
    Optional<MissingPerson> findByIdAndSourceAvailableTrueAndDeletedAtIsNull(UUID id);
}
