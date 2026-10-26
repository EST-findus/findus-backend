package com.findus.backend.comment.controller;

import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.comment.dto.CommentPageResponse;
import com.findus.backend.comment.dto.CommentResponse;
import com.findus.backend.comment.dto.CommentWriteRequest;
import com.findus.backend.comment.service.CommentService;
import com.findus.backend.common.exception.ApiErrorResponse;
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

@Tag(name = "댓글", description = "게시글별 공개 목록과 작성자만 가능한 수정·소프트 삭제")
@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {
	private final CommentService service;
	public CommentController(CommentService service) { this.service = service; }

	@Operation(summary = "댓글 목록", description = "첫 페이지는 0, 기본 10개·최대 100개입니다. 기본 작성 시간·UUID 오름차순이며 sort 허용 필드는 createdAt, updatedAt, id입니다. 삭제된 게시글은 404, 삭제된 댓글은 목록·개수에서 제외합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공"),
		@ApiResponse(responseCode = "400", description = "UUID·페이지·정렬 조건 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	public CommentPageResponse list(@Parameter(description = "게시글 ID (UUID)") @PathVariable UUID postId,
			@ParameterObject @PageableDefault(size = 10, sort = {"createdAt", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
		return service.findAll(postId, pageable);
	}

	@Operation(summary = "댓글 작성", description = "JWT와 CSRF가 필요합니다. 작성자는 로그인 정보에서 결정됩니다. 댓글 내용은 최대 1000자이며 줄바꿈을 보존합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "댓글 작성 완료"),
		@ApiResponse(responseCode = "400", description = "UUID·본문 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "CSRF 검증 실패", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<CommentResponse> create(@Parameter(description = "게시글 ID (UUID)") @PathVariable UUID postId,
			@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody CommentWriteRequest request) {
		return ResponseEntity.status(201).body(service.create(postId, member.id(), request));
	}

	@Operation(summary = "댓글 수정", description = "댓글 작성자만 수정할 수 있습니다. JWT와 CSRF가 필요하며 다른 게시글에 속한 댓글은 404를 반환합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "댓글 수정 완료"),
		@ApiResponse(responseCode = "400", description = "UUID·본문 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "작성자가 아니거나 CSRF 검증 실패", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 또는 댓글 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/{commentId}")
	public CommentResponse update(@Parameter(description = "게시글 ID (UUID)") @PathVariable UUID postId,
			@Parameter(description = "댓글 ID (UUID)") @PathVariable UUID commentId,
			@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody CommentWriteRequest request) {
		return service.update(postId, commentId, member.id(), request);
	}

	@Operation(summary = "댓글 삭제", description = "댓글 작성자만 삭제할 수 있습니다. JWT와 CSRF가 필요하며 DB 행은 보존하고 deleted_at을 기록합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "댓글 삭제 완료", content = @Content),
		@ApiResponse(responseCode = "400", description = "UUID 형식 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "작성자가 아니거나 CSRF 검증 실패", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "게시글 또는 댓글 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> delete(@Parameter(description = "게시글 ID (UUID)") @PathVariable UUID postId,
			@Parameter(description = "댓글 ID (UUID)") @PathVariable UUID commentId,
			@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal member) {
		service.delete(postId, commentId, member.id());
		return ResponseEntity.noContent().build();
	}
}
