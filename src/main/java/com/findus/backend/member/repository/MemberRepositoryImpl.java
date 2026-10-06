package com.findus.backend.member.repository;

import java.util.Optional;
import java.util.UUID;

import com.findus.backend.member.entity.Member;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MemberRepositoryImpl implements MemberRepository {

	private final MemberJpaRepository memberJpaRepository;

	public MemberRepositoryImpl(MemberJpaRepository memberJpaRepository) {
		this.memberJpaRepository = memberJpaRepository;
	}

	@Override
	@Transactional
	public Member save(Member member) {
		return memberJpaRepository.save(member);
	}

	@Override
	public Optional<Member> findById(UUID id) {
		return memberJpaRepository.findByIdAndDeletedAtIsNull(id);
	}

	@Override
	public Optional<Member> findByEmail(String email) {
		return memberJpaRepository.findByEmailAndDeletedAtIsNull(Member.normalizeEmail(email));
	}

	@Override
	public boolean existsByEmail(String email) {
		return memberJpaRepository.existsByEmail(Member.normalizeEmail(email));
	}
}
