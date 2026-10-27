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

    Page<MissingPerson> findAllBySourceAvailableTrueAndDeletedAtIsNullAndOccurrenceDateLessThanEqual(String cutoff,Pageable pageable);
    Optional<MissingPerson> findByIdAndSourceAvailableTrueAndDeletedAtIsNull(UUID id);
    // 대표 사진 검토 화면: 활성 원본이 minImages장 이상인 공개 아동만 조회합니다.
    @org.springframework.data.jpa.repository.Query(value="select p from MissingPerson p where p.sourceAvailable=true and p.deletedAt is null and p.category='010' "
            +"and (select count(i) from MissingPersonImage i where i.missingPerson=p and i.deletedAt is null)>=:minImages",
            countQuery="select count(p) from MissingPerson p where p.sourceAvailable=true and p.deletedAt is null and p.category='010' "
            +"and (select count(i) from MissingPersonImage i where i.missingPerson=p and i.deletedAt is null)>=:minImages")
    Page<MissingPerson> findForMainReview(@org.springframework.data.repository.query.Param("minImages") long minImages,Pageable pageable);
}
