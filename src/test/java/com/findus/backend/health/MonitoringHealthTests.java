package com.findus.backend.health;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("로컬 모니터링 서비스의 실제 HTTP 헬스체크")
class MonitoringHealthTests {
	// region 실제 서비스에 영향을 주지 않는 임시 HTTP 서버
	private HttpServer server;
	private MonitoringHealthChecker checker;
	private final AtomicInteger requests = new AtomicInteger();
	private int readyStatus = 200;
	private String grafanaBody = "{\"database\":\"ok\"}";
	private String targetsBody;

	@BeforeEach
	void 준비() throws Exception {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		targetsBody = targets("up", Instant.now().toString(), "up", Instant.now().toString());
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			int status = path.equals("/-/ready") ? readyStatus : 200;
			String body = path.equals("/api/health") ? grafanaBody
					: path.equals("/api/v1/targets") ? targetsBody : "ready";
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (var output = exchange.getResponseBody()) { output.write(bytes); }
		});
		server.start();
		URI url = URI.create("http://localhost:" + server.getAddress().getPort());
		checker = new MonitoringHealthChecker(new MonitoringHealthProperties(true, url, url), new ObjectMapper());
	}

	@AfterEach
	void 종료() { server.stop(0); }
	// endregion

	// region 정상 응답과 공개 텍스트 형식
	@Test
	@DisplayName("서비스와 최근 수집 결과가 정상이면 7줄의 텍스트와 200을 반환한다")
	void 모든_서비스를_확인한다() throws Exception {
		var result = new HealthStatus(true, true, checker.check());
		assertThat(result.healthy()).isTrue();
		MockMvcBuilders.standaloneSetup(new HealthController(() -> result)).build()
				.perform(get("/health")).andExpect(status().isOk())
				.andExpect(content().string("Spring Boot: ok\nPostgreSQL: ok\nRedis: ok\nPrometheus: ok\nGrafana: ok\nPostgreSQL exporter: ok\nRedis exporter: ok"));
	}
	// endregion

	// region 서비스 실패·오래된 결과·잘못된 응답
	@Test
	@DisplayName("Grafana 내부 DB 실패와 exporter 수집 실패는 해당 항목만 fail로 표시한다")
	void 실패한_항목을_구분한다() throws Exception {
		grafanaBody = "{\"database\":\"fail\",\"internal\":\"공개하면 안 되는 내부 정보\"}";
		targetsBody = targets("down", Instant.now().toString(), "up", Instant.now().toString());
		var result = new HealthStatus(true, true, checker.check());
		assertThat(result.text()).contains("Grafana: fail", "PostgreSQL exporter: fail", "Redis exporter: ok")
				.doesNotContain("내부 정보");
		MockMvcBuilders.standaloneSetup(new HealthController(() -> result)).build()
				.perform(get("/health")).andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("오래된 exporter 성공 결과와 잘못된 Prometheus 응답을 정상으로 오인하지 않는다")
	void 오래되거나_잘못된_결과를_거부한다() {
		targetsBody = targets("up", Instant.now().minusSeconds(90).toString(), "up", "잘못된 날짜");
		assertThat(checker.check()).containsEntry("PostgreSQL exporter", false).containsEntry("Redis exporter", false);
		readyStatus = 503;
		targetsBody = "정상 JSON이 아닙니다";
		assertThat(checker.check()).containsEntry("Prometheus", false).containsEntry("Grafana", true)
				.containsEntry("PostgreSQL exporter", false).containsEntry("Redis exporter", false);
	}

	@Test
	@DisplayName("모니터링 서비스를 종료해도 예외 대신 확인 실패를 반환한다")
	void 서비스_연결_실패를_처리한다() {
		server.stop(0);
		assertThat(checker.check()).hasSize(4).allSatisfy((name, available) -> assertThat(available).isFalse());
	}
	// endregion

	// region 로컬 외 환경의 범위
	@Test
	@DisplayName("비활성화된 환경에서는 외부 요청을 보내지 않고 기존 DB·Redis 검사만 유지한다")
	void 비활성화하면_외부_서비스를_조회하지_않는다() {
		var disabled = new MonitoringHealthChecker(new MonitoringHealthProperties(false, null, null), new ObjectMapper());
		assertThat(disabled.check()).isEmpty();
		assertThat(requests.get()).isZero();
	}
	// endregion

	private String targets(String postgresHealth, String postgresTime, String redisHealth, String redisTime) {
		return "{\"status\":\"success\",\"data\":{\"activeTargets\":["
				+ "{\"labels\":{\"job\":\"findus-postgres\"},\"health\":\"" + postgresHealth + "\",\"lastScrape\":\"" + postgresTime + "\"},"
				+ "{\"labels\":{\"job\":\"findus-redis\"},\"health\":\"" + redisHealth + "\",\"lastScrape\":\"" + redisTime + "\"}]}}";
	}
}
