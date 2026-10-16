package com.findus.backend.favorite.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.favorite.entity.MemberFavorite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 취소한 행도 조회할 수 있어야 재등록 시 중복 생성하지 않고 복원할 수 있습니다. */
interface MemberFavoriteJpaRepository extends JpaRepository<MemberFavorite,UUID> {
    Optional<MemberFavorite> findByMemberIdAndMissingPersonId(UUID memberId,UUID missingPersonId);

    @EntityGraph(attributePaths="missingPerson")
    Page<MemberFavorite> findAllByMemberIdAndDeletedAtIsNull(UUID memberId,Pageable page);
}
