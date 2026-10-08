package com.findus.backend.health;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
	private final HealthService service;

	public HealthController(HealthService service) {
		this.service = service;
	}

	@GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> health() {
		var result = service.check();
		return ResponseEntity.status(result.healthy() ? 200 : 503)
				.contentType(MediaType.TEXT_PLAIN).header("Cache-Control", "no-store").body(result.text());
	}
}
