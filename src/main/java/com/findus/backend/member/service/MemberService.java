package com.findus.backend.member.service;

import java.util.UUID;
import com.findus.backend.member.dto.MemberInfoResponse;
import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.dto.MemberSignupResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface MemberService {
	MemberSignupResponse signup(@NotNull @Valid MemberSignupRequest request);
	MemberInfoResponse findMe(@NotNull UUID id);
}
