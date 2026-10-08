package com.findus.backend.testboard;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팀원용 테스트 화면입니다. 기본 local 프로필에서만 접근할 수 있습니다. */
@Profile("local")
@RestController
public class TestBoardController {
	@GetMapping("/test/mainboard")
	public ResponseEntity<Resource> mainboard() {
		return resource("mainboard.html", "text/html;charset=UTF-8");
	}

	@GetMapping("/test/mainboard.css")
	public ResponseEntity<Resource> stylesheet() {
		return resource("mainboard.css", "text/css;charset=UTF-8");
	}

	@GetMapping("/test/mainboard.js")
	public ResponseEntity<Resource> script() {
		return resource("mainboard.js", "text/javascript;charset=UTF-8");
	}

	private ResponseEntity<Resource> resource(String file, String contentType) {
		// 공개 static 폴더를 사용하지 않아 다른 프로필에서 파일을 직접 열 수 없게 합니다.
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
				.header("Cache-Control", "no-store")
				.header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
						+ "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
				.body(new ClassPathResource("test-ui/" + file));
	}
}
