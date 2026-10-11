package com.findus.backend.post.controller;

import java.net.URI;
import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.post.dto.PostPageResponse;
import com.findus.backend.post.dto.PostResponse;
import com.findus.backend.post.dto.PostWriteRequest;
import com.findus.backend.post.service.PostService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/posts")
public class PostController {
	private final PostService service;

	public PostController(PostService service) { this.service = service; }

	@GetMapping
	public PostPageResponse list(@PageableDefault(size = 10, sort = {"createdAt", "id"},
			direction = Sort.Direction.DESC) Pageable pageable) {
		return service.findAll(pageable);
	}

	@GetMapping("/{id}")
	public PostResponse detail(@PathVariable UUID id) { return service.findById(id); }

	@PostMapping
	public ResponseEntity<PostResponse> create(@AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody PostWriteRequest request) {
		// 작성자 ID는 요청 본문이 아니라 검증된 로그인 정보에서 가져옵니다.
		var result = service.create(member.id(), request);
		return ResponseEntity.created(URI.create("/api/posts/" + result.id())).body(result);
	}

	@PutMapping("/{id}")
	public PostResponse update(@PathVariable UUID id, @AuthenticationPrincipal MemberPrincipal member,
			@Valid @RequestBody PostWriteRequest request) {
		return service.update(id, member.id(), request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal MemberPrincipal member) {
		service.delete(id, member.id());
		return ResponseEntity.noContent().build();
	}
}
