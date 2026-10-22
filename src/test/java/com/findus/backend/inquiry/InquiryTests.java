package com.findus.backend.inquiry;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("서비스 문의 접수·검증·레이트 리밋·관리자 처리 통합 테스트")
class InquiryTests {
	// region 실제 개발 DB와 분리된 테스트 환경
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");
	@Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379).withCommand("redis-server", "--requirepass", "inquiry-test-password");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "inquiry-test-password");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired MemberRepository members;
	@Autowired StringRedisTemplate redis;
	private static final String PASSWORD = "Inquiry-test123!";
	private static final String URL = "/api/inquiries";
	private static final String ADMIN_URL = "/api/admin/inquiries";
	// 테스트마다 다른 IP를 써서 레이트 리밋이 서로 섞이지 않게 합니다.
	private static final AtomicInteger IP = new AtomicInteger(1);

	@BeforeEach
	void 임시_데이터를_초기화한다() {
		jdbc.update("DELETE FROM findus.inquiries");
		jdbc.update("DELETE FROM findus.members");
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}
	// endregion

	// region 접수
	@Test
	@DisplayName("비회원 문의를 CSRF 없이 접수하고 본문·이메일은 응답하지 않는다")
	void 비회원_문의를_접수한다() throws Exception {
		var body = valid();
		body.put("message", "  첫 줄입니다.\n둘째 줄입니다.  ");
		body.put("email", "user@example.test");
		body.put("agree", true);
		var result = submit(body, ip()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists()).andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.message").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
		var row = jdbc.queryForMap("SELECT * FROM findus.inquiries WHERE id = ?", id);
		assertThat(row.get("member_id")).isNull();
		assertThat(row.get("type")).isEqualTo("BUG");
		assertThat(row.get("status")).isEqualTo("RECEIVED");
		assertThat(row.get("message")).isEqualTo("첫 줄입니다.\n둘째 줄입니다.");
		assertThat(row.get("email")).isEqualTo("user@example.test");
		assertThat(row.get("email_agreed_at")).isNotNull();
		assertThat(row.get("page")).isEqualTo("/search");
	}

	@Test
	@DisplayName("유효한 JWT면 회원과 연결하고, 무효한 JWT면 401 대신 비회원으로 접수한다")
	void 회원_연결과_무효_토큰을_처리한다() throws Exception {
		var member = member(false);
		var body = valid();
		body.put("type", "FEATURE");
		submit(body, ip(), login(member)).andExpect(status().isCreated());
		submit(valid(), ip(), "Bearer invalid-token").andExpect(status().isCreated());
		assertThat(jdbc.queryForList("SELECT member_id FROM findus.inquiries WHERE type = 'FEATURE'", UUID.class))
				.containsExactly(member.getId());
		assertThat(jdbc.queryForList("SELECT member_id FROM findus.inquiries WHERE type = 'BUG'", UUID.class))
				.containsExactly((UUID) null);
	}

	static Stream<Arguments> invalidBodies() {
		return Stream.of(
				Arguments.of("type", null, "문의 유형을 골라 주세요."),
				Arguments.of("type", "모르는 유형", "문의 유형을 골라 주세요."),
				Arguments.of("message", " 123456789 ", "내용을 10자 이상 적어 주세요."),
				Arguments.of("message", "가".repeat(1001), "내용은 1000자까지 적을 수 있어요."),
				Arguments.of("email", "user@localhost", "이메일 주소를 다시 확인해 주세요."));
	}

	@ParameterizedTest
	@MethodSource("invalidBodies")
	@DisplayName("프론트 validateInquiry와 같은 문구로 400을 반환하고 저장하지 않는다")
	void 입력을_검증한다(String field, String value, String message) throws Exception {
		var body = valid();
		body.put(field, value);
		submit(body, ip()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(message))
				.andExpect(jsonPath("$.errors[0].field").value(field));
		assertThat(count()).isZero();
	}

	@Test
	@DisplayName("이메일만 있고 동의하지 않으면 400을 반환한다")
	void 이메일_동의를_검증한다() throws Exception {
		var body = valid();
		body.put("email", "user@example.test");
		body.put("agree", false);
		submit(body, ip()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("답변을 받으려면 이메일 수집에 동의해 주세요."))
				.andExpect(jsonPath("$.errors[0].field").value("agree"));
		assertThat(count()).isZero();
	}

	@Test
	@DisplayName("허니팟에 값이 있으면 검증·저장 없이 성공처럼 응답한다")
	void 허니팟을_조용히_무시한다() throws Exception {
		var body = valid();
		body.put("website", "https://spam.example.test");
		body.put("message", "짧음");
		submit(body, ip()).andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists());
		assertThat(count()).isZero();
	}

	@Test
	@DisplayName("같은 IP는 10분에 5건까지 받고 초과하면 429를 반환한다")
	void 레이트_리밋을_적용한다() throws Exception {
		String ip = ip();
		for (int i = 0; i < 5; i++) submit(valid(), ip + ", 10.0.0.1").andExpect(status().isCreated());
		submit(valid(), ip).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		submit(valid(), ip()).andExpect(status().isCreated());
		assertThat(count()).isEqualTo(6);
	}
	// endregion

	// region 관리자
	@Test
	@DisplayName("관리자 API는 비회원 401, 일반 회원 403을 반환한다")
	void 관리자만_접근한다() throws Exception {
		mvc.perform(get(ADMIN_URL)).andExpect(status().isUnauthorized());
		String user = login(member(false));
		mvc.perform(get(ADMIN_URL).header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(get(ADMIN_URL + "/" + UUID.randomUUID()).header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(delete(ADMIN_URL + "/" + UUID.randomUUID()).header("Authorization", user).with(this::withCsrf))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("관리자 목록은 최신순·필터·페이지를 지원하고 상세는 내부 정보를 포함한다")
	void 관리자_목록과_상세를_조회한다() throws Exception {
		String admin = login(member(true));
		UUID oldest = create("BUG", "2026-01-01T00:00:00");
		UUID middle = create("FEATURE", "2026-01-02T00:00:00");
		UUID newest = create("BUG", "2026-01-03T00:00:00");
		jdbc.update("UPDATE findus.inquiries SET status = 'IN_PROGRESS' WHERE id = ?", middle);

		mvc.perform(get(ADMIN_URL).header("Authorization", admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].id").value(newest.toString()))
				.andExpect(jsonPath("$.content[2].id").value(oldest.toString()));
		mvc.perform(get(ADMIN_URL).param("type", "BUG").header("Authorization", admin))
				.andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(get(ADMIN_URL).param("status", "IN_PROGRESS").header("Authorization", admin))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(middle.toString()));
		mvc.perform(get(ADMIN_URL).param("size", "1").param("page", "1").header("Authorization", admin))
				.andExpect(jsonPath("$.content[0].id").value(middle.toString()))
				.andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.hasNext").value(true));
		// 공통 설정(spring.data.web.pageable.max-page-size)이 100을 넘는 크기를 100으로 줄입니다.
		mvc.perform(get(ADMIN_URL).param("size", "101").header("Authorization", admin)).andExpect(jsonPath("$.size").value(100));
		mvc.perform(get(ADMIN_URL).param("sort", "message").header("Authorization", admin)).andExpect(status().isBadRequest());

		mvc.perform(get(ADMIN_URL + "/" + newest).header("Authorization", admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("BUG")).andExpect(jsonPath("$.email").value("user@example.test"))
				.andExpect(jsonPath("$.page").value("/search")).andExpect(jsonPath("$.adminMemo").isEmpty())
				.andExpect(jsonPath("$.memberId").isEmpty());
		mvc.perform(get(ADMIN_URL + "/" + UUID.randomUUID()).header("Authorization", admin)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
	}

	@Test
	@DisplayName("답변 완료로 바꾸면 이메일을 지우고 답변 시간을 기록하며 이전 상태로 되돌릴 수 없다")
	void 상태를_처리한다() throws Exception {
		String admin = login(member(true));
		UUID id = create("BUG", "2026-01-01T00:00:00");
		String url = ADMIN_URL + "/" + id;
		update(url, admin, "ANSWERED", "메일로 답변함").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ANSWERED")).andExpect(jsonPath("$.email").isEmpty())
				.andExpect(jsonPath("$.adminMemo").value("메일로 답변함")).andExpect(jsonPath("$.answeredAt").exists());
		var row = jdbc.queryForMap("SELECT email, answered_at FROM findus.inquiries WHERE id = ?", id);
		assertThat(row.get("email")).isNull();
		assertThat(row.get("answered_at")).isNotNull();

		update(url, admin, "CLOSED", null).andExpect(status().isOk());
		assertThat(jdbc.queryForMap("SELECT answered_at FROM findus.inquiries WHERE id = ?", id).get("answered_at"))
				.isEqualTo(row.get("answered_at"));
		update(url, admin, "RECEIVED", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INQUIRY_STATUS_CONFLICT"));
		mvc.perform(put(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CLOSED\"}")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("소프트 삭제한 문의는 목록·상세에서 제외한다")
	void 문의를_소프트_삭제한다() throws Exception {
		String admin = login(member(true));
		UUID id = create("ETC", "2026-01-01T00:00:00");
		mvc.perform(delete(ADMIN_URL + "/" + id).header("Authorization", admin).with(this::withCsrf))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		assertThat(jdbc.queryForMap("SELECT deleted_at FROM findus.inquiries WHERE id = ?", id).get("deleted_at")).isNotNull();
		mvc.perform(get(ADMIN_URL).header("Authorization", admin)).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(ADMIN_URL + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
		mvc.perform(delete(ADMIN_URL + "/" + id).header("Authorization", admin).with(this::withCsrf))
				.andExpect(status().isNotFound());
		update(ADMIN_URL + "/" + id, admin, "CLOSED", null).andExpect(status().isNotFound());
	}
	// endregion

	// region 도우미
	private Map<String, Object> valid() {
		var body = new HashMap<String, Object>();
		body.put("type", "오류 신고");
		body.put("message", "검색 화면에서 필터가 적용되지 않아요.");
		body.put("email", "");
		body.put("agree", false);
		body.put("website", "");
		body.put("page", "/search");
		return body;
	}

	private String ip() { return "203.0.113." + IP.getAndIncrement(); }

	private ResultActions submit(Map<String, Object> body, String ip) throws Exception {
		return mvc.perform(post(URL).header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
	}

	private ResultActions submit(Map<String, Object> body, String ip, String authorization) throws Exception {
		return mvc.perform(post(URL).header("X-Forwarded-For", ip).header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
	}

	/** 이메일 동의한 문의를 만들고 접수 시간을 정해 정렬을 확인할 수 있게 합니다. */
	private UUID create(String type, String createdAt) throws Exception {
		var body = valid();
		body.put("type", type);
		body.put("email", "user@example.test");
		body.put("agree", true);
		var result = submit(body, ip()).andExpect(status().isCreated()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
		jdbc.update("UPDATE findus.inquiries SET created_at = ? WHERE id = ?", LocalDateTime.parse(createdAt), id);
		return id;
	}

	private ResultActions update(String url, String admin, String status, String memo) throws Exception {
		var body = new HashMap<String, Object>();
		body.put("status", status);
		body.put("adminMemo", memo);
		return mvc.perform(put(url).header("Authorization", admin).with(this::withCsrf)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
	}

	private long count() { return jdbc.queryForObject("SELECT count(*) FROM findus.inquiries", Long.class); }

	private Member member(boolean admin) {
		var member = members.save(new Member("inquiry-" + UUID.randomUUID() + "@example.test", encoder.encode(PASSWORD), "가상회원", "가상닉네임"));
		if (admin) jdbc.update("UPDATE findus.members SET role = 'ADMIN' WHERE id = ?", member.getId());
		return member;
	}

	private String login(Member member) throws Exception {
		var result = mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", member.getEmail(), "password", PASSWORD))))
				.andExpect(status().isOk()).andReturn();
		return "Bearer " + mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
	}

	private MockHttpServletRequest withCsrf(MockHttpServletRequest request) {
		try {
			var result = mvc.perform(get("/api/auth/csrf")).andReturn();
			var csrf = mapper.readTree(result.getResponse().getContentAsString());
			request.addHeader(csrf.get("headerName").asString(), csrf.get("token").asString());
			request.setCookies(new Cookie[] {result.getResponse().getCookie("XSRF-TOKEN")});
			return request;
		} catch (Exception e) { throw new IllegalStateException("테스트 CSRF 준비 실패", e); }
	}
	// endregion
}
