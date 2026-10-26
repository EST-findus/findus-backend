package com.findus.backend.member.controller;

import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.member.dto.MemberInfoResponse;
import com.findus.backend.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원가입 및 내 정보 조회")
@RestController
public class MemberInfoController {
	private final MemberService members;
	public MemberInfoController(MemberService members) { this.members = members; }

	@Operation(summary = "내 정보 조회", description = "로그인 후 Authorize에 accessToken을 입력하세요. 비밀번호와 해시는 반환하지 않습니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
		@ApiResponse(responseCode = "401", description = "로그인이 필요함", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/api/members/me")
	public MemberInfoResponse me(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
		return members.findMe(principal.id());
	}
}
