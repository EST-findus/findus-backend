package com.findus.backend.testboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 화면과 파일 경로를 확인합니다. 실제 버튼 동작은 브라우저에서 별도로 검증합니다. */
class TestBoardTests {
	@Test
	@DisplayName("테스트 화면이 한국어로 열리고 연결된 CSS·JS도 제공한다")
	void 테스트_화면과_필요한_파일을_제공한다() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new TestBoardController()).build();
		mvc.perform(get("/test/mainboard")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/html"))
				.andExpect(content().string(containsString("테스트 보드")))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
		mvc.perform(get("/test/mainboard.css")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
		mvc.perform(get("/test/mainboard.js")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
	}
}
