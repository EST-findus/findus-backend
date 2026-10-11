package com.findus.backend.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.monitoring.enabled=true", "app.monitoring.password=monitoring-test-password-only",
		"management.server.port=0", "management.endpoints.web.exposure.include=prometheus",
		"management.endpoints.web.discovery.enabled=false", "management.endpoint.prometheus.access=read-only",
		"management.prometheus.metrics.export.enabled=true", "management.metrics.tags.application=findus-backend",
		"management.metrics.distribution.percentiles-histogram.http.server.requests=true"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("모니터링 포트·인증·실제 메트릭 통합 테스트")
class MonitoringTests {
	// region 개발 데이터에 영향을 주지 않는 임시 환경
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");
	@Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379).withCommand("redis-server", "--requirepass", "monitoring-test-redis");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "monitoring-test-redis");
	}

	@LocalServerPort int apiPort;
	@Value("${local.management.port}") int monitoringPort;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	// endregion

	// region 수집 계정 인증과 권한 구분
	@Test
	@DisplayName("메트릭은 수집 계정으로만 읽을 수 있다")
	void 비밀번호가_맞아야_메트릭을_읽을_수_있다() throws Exception {
		var anonymous = get(monitoringPort, "/actuator/prometheus", null);
		assertThat(anonymous.statusCode()).isEqualTo(401);
		assertThat(anonymous.headers().firstValue("WWW-Authenticate")).contains("Basic realm=\"FindUs monitoring\"");
		assertThat(get(monitoringPort, "/actuator/prometheus", basic("wrong-password")).statusCode()).isEqualTo(401);
		var response = get(monitoringPort, "/actuator/prometheus", basic("monitoring-test-password-only"));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("jvm_memory_used_bytes", "application=\"findus-backend\"")
				.doesNotContain("monitoring-test-password-only", "monitoring-test-redis");
	}

	@Test
	@DisplayName("환경변수·덤프·설정 조회 경로는 수집 계정에도 공개하지 않는다")
	void 수집_경로_외의_Actuator는_차단한다() throws Exception {
		for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/configprops"}) {
			assertThat(get(monitoringPort, path, basic("monitoring-test-password-only")).statusCode())
					.as("수집 계정으로 %s를 조회해도 거부해야 합니다.", path).isEqualTo(403);
		}
	}

	@Test
	@DisplayName("회원 API와 메트릭 포트는 구분하며 수집 계정으로 회원 API에 로그인할 수 없다")
	void 수집_계정과_회원_인증을_구분한다() throws Exception {
		String credentials = basic("monitoring-test-password-only");
		assertThat(apiPort).isNotEqualTo(monitoringPort);
		assertThat(get(apiPort, "/actuator/prometheus", credentials).statusCode()).isEqualTo(404);
		assertThat(get(apiPort, "/api/members/me", credentials).statusCode()).isEqualTo(401);
		var health = get(apiPort, "/health", null);
		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).isEqualTo("Spring Boot: ok\nPostgreSQL: ok\nRedis: ok");
	}
	// endregion

	// region 요청 수·응답 시간·DB 연결의 실제 측정값
	@Test
	@DisplayName("API 요청 후 HTTP·JVM·DB 커넥션 지표를 수집할 수 있다")
	void API요청이_실제_메트릭에_반영된다() throws Exception {
		assertThat(get(apiPort, "/api/posts", null).statusCode()).isEqualTo(200);
		var metrics = get(monitoringPort, "/actuator/prometheus", basic("monitoring-test-password-only"));
		assertThat(metrics.statusCode()).isEqualTo(200);
		assertThat(metrics.body()).contains("http_server_requests_seconds_count", "uri=\"/api/posts\"",
				"http_server_requests_seconds_bucket", "process_cpu_usage", "hikaricp_connections_active");
	}
	// endregion

	private HttpResponse<String> get(int port, String path, String authorization) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(10)).GET();
		if (authorization != null) request.header("Authorization", authorization);
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private String basic(String password) {
		return "Basic " + Base64.getEncoder().encodeToString(("prometheus:" + password)
				.getBytes(StandardCharsets.UTF_8));
	}
}
