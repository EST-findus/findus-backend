package com.findus.backend.member.service;

import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.dto.MemberSignupResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface MemberService {
	MemberSignupResponse signup(@NotNull @Valid MemberSignupRequest request);
}
