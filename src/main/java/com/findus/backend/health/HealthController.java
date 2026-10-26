package com.findus.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상태 확인", description = "서버·DB·Redis 및 로컬 모니터링 연결 상태")
@RestController
public class HealthController {
	private final HealthService service;

	public HealthController(HealthService service) {
		this.service = service;
	}

	@Operation(summary = "연결 상태 확인", description = "일반 텍스트로 연결 상태를 반환합니다. DB는 SELECT 1, Redis는 PING으로 확인합니다. 로컬에서는 Prometheus·Grafana와 두 exporter도 검사합니다. exporter는 45초 이내의 Prometheus 수집 성공 결과로 확인하므로 중단 반영에 지연이 있을 수 있습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "모든 연결 정상"),
		@ApiResponse(responseCode = "503", description = "하나 이상의 서비스 확인 실패")
	})
	@GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> health() {
		var result = service.check();
		return ResponseEntity.status(result.healthy() ? 200 : 503)
				.contentType(MediaType.TEXT_PLAIN).header("Cache-Control", "no-store").body(result.text());
	}
}
