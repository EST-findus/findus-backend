package com.findus.backend.missing.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.missing.entity.MissingPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

interface MissingPersonJpaRepository extends JpaRepository<MissingPerson,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from MissingPerson p where p.id = :id")
    Optional<MissingPerson> findForImageUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select p from MissingPerson p where p.id = :id and p.deletedAt is null")
    Optional<MissingPerson> findForFavorite(@org.springframework.data.repository.query.Param("id") UUID id);

    Page<MissingPerson> findAllBySourceAvailableTrueAndDeletedAtIsNull(Pageable pageable);
    Optional<MissingPerson> findByIdAndSourceAvailableTrueAndDeletedAtIsNull(UUID id);
}
