package com.findus.backend.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.controller.AuthController;
import com.findus.backend.auth.service.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AuthTests.AdminTestController.class)
@DisplayName("로그인·JWT·Redis 인증 통합 테스트")
class AuthTests {
	// region 실제 개발 환경과 분리된 테스트 DB·Redis
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");
	@Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379).withCommand("redis-server", "--requirepass", "auth-test-password");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "auth-test-password");
	}

	@Autowired MockMvc mvc;
	@Autowired org.springframework.context.ApplicationContext context;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired StringRedisTemplate redis;
	@Autowired JwtTokenService tokens;
	@Autowired JwtEncoder encoder;
	@Autowired AuthProperties properties;
	private static final String PASSWORD = "Login-test123!";

	// 관리자 경로의 권한 설정을 검증하기 위한 테스트 전용 API입니다.
	@RestController
	static class AdminTestController {
		@GetMapping("/api/admin/check") String check() { return "관리자 확인"; }
	}
	// endregion

	// region 로그인과 비밀번호·계정 보호
	@Test
	@DisplayName("가입 후 로그인하면 Access 응답과 HttpOnly Refresh 쿠키를 받는다")
	void 로그인과_내정보_조회에_성공한다() throws Exception {
		String email = signup();
		Session session = login(" " + email.toUpperCase(java.util.Locale.ROOT) + " ");
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + session.access()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
		String sid = tokens.verify(session.access(), "access").getClaimAsString("sid");
		String key = "findus:auth:session:" + sid;
		assertThat(redis.opsForValue().get(key)).doesNotContain(session.refresh().getValue());
		assertThat(redis.getExpire(key)).isBetween(1L, properties.refreshTtl().getSeconds());
	}

	@Test
	@DisplayName("없는 계정과 틀린 비밀번호는 같은 401 응답으로 처리한다")
	void 로그인_실패가_계정_존재여부를_노출하지_않는다() throws Exception {
		String email = signup();
		var wrong = loginRequest(email, "Wrong-password123!");
		var missing = loginRequest("missing-" + UUID.randomUUID() + "@example.com", PASSWORD);
		assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
		assertThat(missing.getResponse().getStatus()).isEqualTo(401);
		assertThat(wrong.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
		assertThat(wrong.getResponse().getContentAsString()).doesNotContain(email, PASSWORD, "Wrong-password123!");
	}

	@Test
	@DisplayName("비활성·삭제된 회원과 지원하지 않는 비밀번호 해시는 로그인할 수 없다")
	void 비활성_삭제_회원의_로그인을_거부한다() throws Exception {
		String email = signup();
		jdbc.update("UPDATE findus.members SET status = 'INACTIVE' WHERE email = ?", email);
		assertThat(loginRequest(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
		jdbc.update("UPDATE findus.members SET deleted_at = CURRENT_TIMESTAMP WHERE email = ?", email);
		assertThat(loginRequest(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
		String malformed = signup();
		jdbc.update("UPDATE findus.members SET password_hash = 'unsupported' WHERE email = ?", malformed);
		assertThat(loginRequest(malformed, PASSWORD).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("로그인 입력 누락은 400으로 반환한다")
	void 로그인_입력을_검증한다() throws Exception {
		mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}
	// endregion

	// region 토큰 검증과 최신 회원 상태·권한
	@Test
	@DisplayName("토큰 없음·변조·만료·잘못된 발급자·Refresh 사용은 401로 차단한다")
	void 잘못된_토큰을_거부한다() throws Exception {
		Session session = login(signup());
		var jwt = tokens.verify(session.access(), "access");
		mvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
		var parts = session.access().split("\\.");
		String tampered = parts[0] + ".e30." + parts[2];
		for (String token : List.of(tampered, session.refresh().getValue(),
				customToken(jwt.getSubject(), jwt.getClaimAsString("sid"), properties.issuer(), Instant.now().minusSeconds(5)),
				customToken(jwt.getSubject(), jwt.getClaimAsString("sid"), "wrong-issuer", Instant.now().plusSeconds(60)),
				customToken(jwt.getSubject(), "invalid-uuid", properties.issuer(), Instant.now().plusSeconds(60)))) {
			mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("로그인 후 비활성화·삭제된 회원의 기존 Access도 사용할 수 없다")
	void 계정상태_변경을_즉시_반영한다() throws Exception {
		String email = signup();
		Session session = login(email);
		jdbc.update("UPDATE findus.members SET status = 'INACTIVE' WHERE email = ?", email);
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + session.access()))
				.andExpect(status().isUnauthorized());
		assertThat(refresh(session.refresh()).getResponse().getStatus()).isEqualTo(401);
		jdbc.update("UPDATE findus.members SET deleted_at = CURRENT_TIMESTAMP WHERE email = ?", email);
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + session.access()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("USER는 관리자 경로에 403을 받고 DB에서 ADMIN으로 변경하면 접근할 수 있다")
	void 최신_권한으로_관리자_접근을_검증한다() throws Exception {
		String email = signup();
		Session session = login(email);
		mvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + session.access()))
				.andExpect(status().isForbidden());
		jdbc.update("UPDATE findus.members SET role = 'ADMIN' WHERE email = ?", email);
		mvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + session.access()))
				.andExpect(status().isOk());
	}
	// endregion

	// region 재발급·동시 요청·로그아웃
	@Test
	@DisplayName("재발급은 Refresh를 교체하고 이전 Refresh의 재사용을 거부한다")
	void 재발급하면_이전_Refresh는_사용할_수_없다() throws Exception {
		Session first = login(signup());
		MvcResult result = refresh(first.refresh());
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		Session second = session(result);
		assertThat(second.access()).isNotEqualTo(first.access());
		assertThat(second.refresh().getValue()).isNotEqualTo(first.refresh().getValue());
		assertThat(refresh(first.refresh()).getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(second.refresh()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("같은 Refresh로 동시 재발급하면 한 요청만 성공한다")
	void 동시_재발급은_한번만_성공한다() throws Exception {
		Session session = login(signup());
		var barrier = new java.util.concurrent.CyclicBarrier(2);
		var pool = Executors.newFixedThreadPool(2);
		try {
			var first = pool.submit(() -> { barrier.await(); return refresh(session.refresh()).getResponse().getStatus(); });
			var second = pool.submit(() -> { barrier.await(); return refresh(session.refresh()).getResponse().getStatus(); });
			assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 401);
		} finally { pool.shutdownNow(); }
	}

	@Test
	@DisplayName("로그아웃하면 쿠키·Redis 세션을 지우고 해당 로그인 토큰을 즉시 차단한다")
	void 로그아웃하면_Access와_Refresh를_폐기한다() throws Exception {
		String email = signup();
		Session first = login(email);
		Session otherDevice = login(email);
		var result = mvc.perform(post("/api/auth/logout").with(this::withCsrf).cookie(first.refresh()))
				.andExpect(status().isNoContent()).andReturn();
		assertThat(result.getResponse().getCookie(AuthController.REFRESH_COOKIE).getMaxAge()).isZero();
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + first.access()))
				.andExpect(status().isUnauthorized());
		assertThat(refresh(first.refresh()).getResponse().getStatus()).isEqualTo(401);
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + otherDevice.access()))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Refresh 쿠키 없음·변조·Access를 대신 사용한 요청은 거부한다")
	void 잘못된_Refresh_요청을_거부한다() throws Exception {
		Session session = login(signup());
		mvc.perform(post("/api/auth/refresh").with(this::withCsrf)).andExpect(status().isUnauthorized());
		assertThat(refresh(new Cookie(AuthController.REFRESH_COOKIE, "invalid")).getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(new Cookie(AuthController.REFRESH_COOKIE, session.access())).getResponse().getStatus()).isEqualTo(401);
		String sid = tokens.verify(session.access(), "access").getClaimAsString("sid");
		redis.delete("findus:auth:session:" + sid);
		assertThat(refresh(session.refresh()).getResponse().getStatus()).isEqualTo(401);
	}
	// endregion

	// region 브라우저 쿠키·CSRF·CORS
	@Test
	@DisplayName("실제 발급된 CSRF 쿠키와 헤더로 로그인할 수 있고 누락하면 403을 반환한다")
	void 실제_CSRF_쿠키와_헤더를_검증한다() throws Exception {
		String email = signup();
		var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		var body = mapper.readTree(csrfResult.getResponse().getContentAsString());
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrfCookie).isNotNull();
		mvc.perform(post("/api/auth/login").cookie(csrfCookie)
						.header(body.get("headerName").asText(), body.get("token").asText())
						.contentType(MediaType.APPLICATION_JSON).content(loginBody(email, PASSWORD)))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email, PASSWORD)))
				.andExpect(status().isForbidden());
		Session session = login(email);
		mvc.perform(post("/api/auth/refresh").cookie(session.refresh())).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/logout").cookie(session.refresh())).andExpect(status().isForbidden());
		assertThat(refresh(session.refresh()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("허용된 React 주소만 쿠키를 포함한 요청을 보낼 수 있다")
	void CORS_허용_주소를_검증한다() throws Exception {
		var allowed = mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
				.andExpect(status().isOk()).andReturn();
		assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
		assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
		mvc.perform(options("/api/auth/login").header("Origin", "https://unknown.example")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden());
	}
	// endregion

	// region 전체 인증 흐름과 React 요청 검증
	@Test
	@DisplayName("가입부터 재발급·로그아웃까지 이어서 검증하고 이전 토큰을 차단한다")
	void 전체_인증_흐름을_검증한다() throws Exception {
		Session first = login(signup());
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + first.access()))
				.andExpect(status().isOk());
		MvcResult refreshed = refresh(first.refresh());
		assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
		Session second = session(refreshed);
		mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + second.access()))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").with(this::withCsrf).cookie(second.refresh()))
				.andExpect(status().isNoContent());
		for (Session old : List.of(first, second)) {
			mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + old.access()))
					.andExpect(status().isUnauthorized());
			assertThat(refresh(old.refresh()).getResponse().getStatus()).isEqualTo(401);
		}
	}

	@Test
	@DisplayName("React 주소의 실제 로그인·조회·재발급·로그아웃 요청에 CORS 헤더를 반환한다")
	void React_인증_요청을_검증한다() throws Exception {
		String origin = "http://localhost:5173";
		var csrf = mvc.perform(get("/api/auth/csrf").header("Origin", origin))
				.andExpect(status().isOk()).andReturn();
		var body = mapper.readTree(csrf.getResponse().getContentAsString());
		var loggedIn = mvc.perform(post("/api/auth/login").header("Origin", origin)
				.cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
				.header(body.get("headerName").asText(), body.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON).content(loginBody(signup(), PASSWORD)))
				.andExpect(status().isOk()).andReturn();
		assertCors(loggedIn, origin);
		Session first = session(loggedIn);
		assertCors(mvc.perform(get("/api/members/me").header("Origin", origin)
				.header("Authorization", "Bearer " + first.access())).andExpect(status().isOk()).andReturn(), origin);
		var refreshed = mvc.perform(post("/api/auth/refresh").header("Origin", origin)
				.with(this::withCsrf).cookie(first.refresh())).andExpect(status().isOk()).andReturn();
		assertCors(refreshed, origin);
		assertCors(mvc.perform(post("/api/auth/logout").header("Origin", origin)
				.with(this::withCsrf).cookie(session(refreshed).refresh()))
				.andExpect(status().isNoContent()).andReturn(), origin);
		// 미허용 주소는 실제 요청도 거부합니다. 토큰 발급이나 세션 생성으로 이어지지 않습니다.
		mvc.perform(post("/api/auth/login").header("Origin", "https://unknown.example")
				.with(this::withCsrf).contentType(MediaType.APPLICATION_JSON).content(loginBody(signup(), PASSWORD)))
				.andExpect(status().isForbidden());
	}

	private void assertCors(MvcResult result, String origin) {
		assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
		assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
	}

	@Test
	@DisplayName("로그인 없이 실제 DB·Redis 연결 상태를 세 줄의 텍스트로 반환한다")
	void 서버와_DB_Redis_연결을_확인한다() throws Exception {
		mvc.perform(get("/health")).andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("Spring Boot: ok\nPostgreSQL: ok\nRedis: ok"));
		var result = mvc.perform(get("/health").header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk()).andReturn();
		assertCors(result, "http://localhost:5173");
		mvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
	}
	// endregion

	// region 테스트 보조 메서드
	private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
		try {
			var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
			var body = mapper.readTree(result.getResponse().getContentAsString());
			request.addHeader(body.get("headerName").asText(), body.get("token").asText());
			var cookies = new java.util.ArrayList<Cookie>();
			if (request.getCookies() != null) { cookies.addAll(List.of(request.getCookies())); }
			cookies.add(result.getResponse().getCookie("XSRF-TOKEN"));
			request.setCookies(cookies.toArray(Cookie[]::new));
			return request;
		} catch (Exception exception) {
			throw new IllegalStateException("테스트 CSRF 준비에 실패했습니다.", exception);
		}
	}

	private String signup() throws Exception {
		String email = "auth-" + UUID.randomUUID() + "@example.com";
		mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
								"name", "인증테스트회원", "nickname", "인증확인"))))
				.andExpect(status().isCreated());
		return email;
	}

	private String loginBody(String email, String password) {
		return mapper.writeValueAsString(Map.of("email", email, "password", password));
	}

	private MvcResult loginRequest(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, password))).andReturn();
	}

	private Session login(String email) throws Exception {
		MvcResult result = loginRequest(email, PASSWORD);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken", PASSWORD);
		assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("expiresIn").asLong()).isEqualTo(900);
		assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
		Session session = session(result);
		assertThat(session.refresh().isHttpOnly()).isTrue();
		assertThat(session.refresh().getPath()).isEqualTo("/api/auth");
		assertThat(session.refresh().getMaxAge()).isEqualTo(604800);
		assertThat(session.refresh().getAttribute("SameSite")).isEqualTo("Lax");
		return session;
	}

	private Session session(MvcResult result) throws Exception {
		return new Session(mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText(),
				result.getResponse().getCookie(AuthController.REFRESH_COOKIE));
	}

	private MvcResult refresh(Cookie cookie) throws Exception {
		return mvc.perform(post("/api/auth/refresh").with(this::withCsrf).cookie(cookie)).andReturn();
	}

	private String customToken(String subject, String sid, String issuer, Instant expires) {
		var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).id(UUID.randomUUID().toString())
				.issuedAt(Instant.now().minusSeconds(60)).expiresAt(expires).claim("sid", sid).claim("type", "access").build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private record Session(String access, Cookie refresh) {
		@Override public String toString() { return "Session[내용 생략]"; }
	}
	// endregion
}
