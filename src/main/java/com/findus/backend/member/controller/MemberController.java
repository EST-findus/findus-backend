package com.findus.backend.member.controller;

import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.dto.MemberSignupResponse;
import com.findus.backend.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MemberSignupResponse signup(@Valid @RequestBody MemberSignupRequest request) {
		return memberService.signup(request);
	}
}
