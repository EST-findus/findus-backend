package com.findus.backend.member.repository;

import java.util.Optional;
import java.util.UUID;

import com.findus.backend.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/** 같은 패키지의 구현체만 사용하는 Spring Data JPA 저장소입니다. */
interface MemberJpaRepository extends JpaRepository<Member, UUID> {

	Optional<Member> findByIdAndDeletedAtIsNull(UUID id);

	Optional<Member> findByEmailAndDeletedAtIsNull(String email);

	boolean existsByEmail(String email);
}
