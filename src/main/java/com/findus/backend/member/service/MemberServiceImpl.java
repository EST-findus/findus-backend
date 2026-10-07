package com.findus.backend.member.service;

import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.dto.MemberSignupResponse;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.exception.DuplicateEmailException;
import com.findus.backend.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class MemberServiceImpl implements MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public MemberServiceImpl(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public MemberSignupResponse signup(MemberSignupRequest request) {
		// 소프트 삭제된 회원의 이메일도 재사용하지 않습니다.
		if (memberRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException();
		}
		Member member = new Member(request.email(), passwordEncoder.encode(request.password()),
				request.name(), request.nickname());
		// 동시에 들어온 요청의 중복은 DB UNIQUE 제약이 최종 방어합니다.
		return MemberSignupResponse.from(memberRepository.save(member));
	}
}
