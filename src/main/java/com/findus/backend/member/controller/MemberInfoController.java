package com.findus.backend.member.controller;

import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.member.dto.MemberInfoResponse;
import com.findus.backend.member.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberInfoController {
	private final MemberService members;
	public MemberInfoController(MemberService members) { this.members = members; }

	@GetMapping("/api/members/me")
	public MemberInfoResponse me(@AuthenticationPrincipal MemberPrincipal principal) {
		return members.findMe(principal.id());
	}
}
