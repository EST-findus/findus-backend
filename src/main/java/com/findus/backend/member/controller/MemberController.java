package com.findus.backend.member.controller;

import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.dto.MemberSignupResponse;
import com.findus.backend.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원가입 및 내 정보 조회")
@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@Operation(summary = "회원가입", description = "테스트 이메일을 사용하세요. 성공하면 회원이 DB에 저장됩니다. 같은 이메일로 다시 가입할 수 없습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "회원가입 완료"),
		@ApiResponse(responseCode = "400", description = "입력 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "이미 가입된 이메일", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MemberSignupResponse signup(@Valid @RequestBody MemberSignupRequest request) {
		return memberService.signup(request);
	}
}
