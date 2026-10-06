package com.findus.backend.member.repository;

import java.util.Optional;
import java.util.UUID;

import com.findus.backend.member.entity.Member;

/** 회원 서비스가 사용할 저장소 계약입니다. 조회는 소프트 삭제된 회원을 제외합니다. */
public interface MemberRepository {

	Member save(Member member);

	Optional<Member> findById(UUID id);

	Optional<Member> findByEmail(String email);

	/** 삭제된 회원도 포함하여 이메일 재사용을 막습니다. */
	boolean existsByEmail(String email);
}
