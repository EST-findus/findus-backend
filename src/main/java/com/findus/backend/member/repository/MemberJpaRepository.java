package com.findus.backend.member.repository;

import java.util.Optional;
import java.util.UUID;

import com.findus.backend.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/** 같은 패키지의 구현체만 사용하는 Spring Data JPA 저장소입니다. */
interface MemberJpaRepository extends JpaRepository<Member, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id and m.deletedAt is null")
    Optional<Member> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);


	Optional<Member> findByIdAndDeletedAtIsNull(UUID id);

	Optional<Member> findByEmailAndDeletedAtIsNull(String email);

	boolean existsByEmail(String email);
}
