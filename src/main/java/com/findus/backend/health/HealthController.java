package com.findus.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상태 확인", description = "서버·DB·Redis 연결 상태")
@RestController
public class HealthController {
	private final HealthService service;

	public HealthController(HealthService service) {
		this.service = service;
	}

	@Operation(summary = "연결 상태 확인", description = "Spring Boot·PostgreSQL·Redis 상태를 세 줄의 일반 텍스트로 반환합니다. DB는 SELECT 1, Redis는 PING으로 확인합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "모든 연결 정상"),
		@ApiResponse(responseCode = "503", description = "DB 또는 Redis 연결 실패")
	})
	@GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> health() {
		var result = service.check();
		return ResponseEntity.status(result.healthy() ? 200 : 503)
				.contentType(MediaType.TEXT_PLAIN).header("Cache-Control", "no-store").body(result.text());
	}
}
