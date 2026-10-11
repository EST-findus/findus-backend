package com.findus.backend.post.controller;

import java.net.URI;
import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.post.dto.PostPageResponse;
import com.findus.backend.post.dto.PostResponse;
import com.findus.backend.post.dto.PostWriteRequest;
import com.findus.backend.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게시글", description = "공개 조회 및 작성자만 가능한 수정·삭제")
@RestController
@RequestMapping("/api/posts")
public class PostController {
	private final PostService service;

	public PostController(PostService service) { this.service = service; }

	@Operation(summary = "게시글 목록", description = "첫 페이지는 0입니다. 기본 10개, 최대 100개이며 삭제된 글은 제외합니다. sort 허용 필드: createdAt, updatedAt, title, id. 기본 정렬은 createdAt,desc와 id,desc입니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공"),
		@ApiResponse(responseCode = "400", description = "허용하지 않는 정렬 또는 페이지 요청", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	public PostPageResponse list(@ParameterObject @PageableDefault(size = 10, sort = {"createdAt", "id"},
			direction = Sort.Direction.DESC) Pageable pageable) {
		return service.findAll(pageable);
	}

	@Operation(summary = "게시글 상세", description = "삭제되었거나 없는 게시글은 404를 반환합니다. 응답의 날짜·시간은 UTC 기준입니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "게시글 상세 조회 성공"),
		@ApiResponse(responseCode = "400", description = "UUID 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/{id}")
	public PostResponse detail(@Parameter(description = "게시글 ID (UUID)", example = "123e4567-e89b-12d3-a456-426614174000") @PathVariable UUID id) { return service.findById(id); }

	@Operation(summary = "게시글 작성", description = "JWT와 CSRF가 필요합니다. 작성자는 로그인 정보에서 결정되며 성공 시 Location 헤더에 게시글 주소를 반환합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "게시글 작성 완료"),
		@ApiResponse(responseCode = "400", description = "입력 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인이 필요함", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<PostResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody PostWriteRequest request) {
		// 작성자 ID는 요청 본문이 아니라 검증된 로그인 정보에서 가져옵니다.
		var result = service.create(member.id(), request);
		return ResponseEntity.created(URI.create("/api/posts/" + result.id())).body(result);
	}

	@Operation(summary = "게시글 수정", description = "작성자만 수정할 수 있습니다. 제목과 본문을 모두 보내세요. JWT와 CSRF가 필요합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "게시글 수정 완료"),
		@ApiResponse(responseCode = "400", description = "입력 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인이 필요함", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "작성자가 아니거나 CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/{id}")
	public PostResponse update(@Parameter(description = "게시글 ID (UUID)", example = "123e4567-e89b-12d3-a456-426614174000") @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody PostWriteRequest request) {
		return service.update(id, member.id(), request);
	}

	@Operation(summary = "게시글 삭제", description = "작성자만 삭제할 수 있습니다. deleted_at을 기록하는 소프트 삭제이며 삭제된 글은 조회에서 제외합니다. JWT와 CSRF가 필요합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "게시글 삭제 완료", content = @Content),
		@ApiResponse(responseCode = "400", description = "UUID 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인이 필요함", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "작성자가 아니거나 CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@Parameter(description = "게시글 ID (UUID)", example = "123e4567-e89b-12d3-a456-426614174000") @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member) {
		service.delete(id, member.id());
		return ResponseEntity.noContent().build();
	}
}
