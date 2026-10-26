package com.findus.backend.inquiry.controller;

import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.inquiry.dto.InquiryCreateRequest;
import com.findus.backend.inquiry.dto.InquiryCreatedResponse;
import com.findus.backend.inquiry.dto.InquiryPageResponse;
import com.findus.backend.inquiry.dto.InquiryResponse;
import com.findus.backend.inquiry.dto.InquiryUpdateRequest;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import com.findus.backend.inquiry.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "문의", description = "서비스 문의(오류·제안·정보 정정) 접수와 관리자 처리. 실종 제보는 받지 않으며 182·112로 안내합니다.")
@RestController
public class InquiryController {
	private final InquiryService service;
	public InquiryController(InquiryService service) { this.service = service; }

	@Operation(summary = "문의 접수", description = "비회원도 보낼 수 있고 CSRF가 필요 없습니다. 유효한 JWT가 있으면 회원으로 연결하고, 없거나 무효면 비회원으로 처리합니다. "
			+ "실종 제보는 받지 않습니다. 같은 IP는 10분에 5건까지 받으며 본문·이메일은 응답에 되돌려주지 않습니다. "
			+ "400 응답의 message에는 화면에 그대로 보여줄 첫 번째 오류 문구가 들어갑니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "문의 접수 완료"),
		@ApiResponse(responseCode = "400", description = "유형·본문·이메일·동의 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "429", description = "짧은 시간에 너무 많은 문의", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/inquiries")
	public ResponseEntity<InquiryCreatedResponse> createInquiry(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member,
			@RequestBody InquiryCreateRequest request, HttpServletRequest http) {
		return ResponseEntity.status(201).body(service.create(member == null ? null : member.id(), clientIp(http), request));
	}

	@Operation(summary = "문의 목록", description = "관리자 전용. 첫 페이지는 0, 기본 20개·최대 100개입니다. 기본 접수 최신순이며 sort 허용 필드는 createdAt입니다. 삭제된 문의는 제외합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "문의 목록 조회 성공"),
		@ApiResponse(responseCode = "400", description = "필터·페이지·정렬 조건 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "관리자 아님", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/api/admin/inquiries")
	public InquiryPageResponse listInquiries(@Parameter(description = "처리 상태 필터") @RequestParam(required = false) InquiryStatus status,
			@Parameter(description = "문의 유형 코드 필터") @RequestParam(required = false) InquiryType type,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.findAll(status, type, pageable);
	}

	@Operation(summary = "문의 상세", description = "관리자 전용. 이메일·보낸 화면·회원 ID·관리자 메모를 포함합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "문의 상세 조회 성공"),
		@ApiResponse(responseCode = "400", description = "UUID 형식 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "관리자 아님", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "문의 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/api/admin/inquiries/{id}")
	public InquiryResponse inquiryDetail(@Parameter(description = "문의 ID (UUID)") @PathVariable UUID id) {
		return service.findById(id);
	}

	@Operation(summary = "문의 처리", description = "관리자 전용, JWT와 CSRF가 필요합니다. 상태와 메모를 함께 바꿉니다. ANSWERED·CLOSED가 되면 답변 시간을 기록하고 이메일을 지웁니다. 이전 상태로 되돌리면 409입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "문의 처리 완료"),
		@ApiResponse(responseCode = "400", description = "UUID·상태·메모 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "관리자 아님 또는 CSRF 검증 실패", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "문의 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "이전 상태로 되돌리기", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/api/admin/inquiries/{id}")
	public InquiryResponse updateInquiry(@Parameter(description = "문의 ID (UUID)") @PathVariable UUID id,
			@Valid @RequestBody InquiryUpdateRequest request) {
		return service.update(id, request);
	}

	@Operation(summary = "문의 삭제", description = "관리자 전용, JWT와 CSRF가 필요합니다. DB 행은 보존하고 deleted_at을 기록합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "문의 삭제 완료", content = @Content),
		@ApiResponse(responseCode = "400", description = "UUID 형식 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "관리자 아님 또는 CSRF 검증 실패", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "문의 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/api/admin/inquiries/{id}")
	public ResponseEntity<Void> deleteInquiry(@Parameter(description = "문의 ID (UUID)") @PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	// shortcut: 백엔드는 Next 서버·리버스 프록시 뒤에서만 접근된다고 보고 X-Forwarded-For 첫 값을 그대로 믿습니다.
	// 백엔드가 직접 노출되면 위조할 수 있으므로 그때는 server.forward-headers-strategy와 신뢰 프록시 설정으로 바꿉니다.
	// 헤더가 없으면 연결 주소를 쓰며, 프록시가 헤더를 넘기지 않으면 모든 문의가 프록시 IP 하나로 묶입니다.
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
		return request.getRemoteAddr();
	}
}
