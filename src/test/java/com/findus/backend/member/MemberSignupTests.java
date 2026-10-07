package com.findus.backend.member;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.findus.backend.member.dto.MemberSignupRequest;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.member.service.MemberService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP 요청부터 실제 PostgreSQL 저장까지 검증합니다. 개발 DB는 건드리지 않습니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("회원가입 API 통합 테스트")
class MemberSignupTests {

	// region 테스트 전용 DB와 도구 준비
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.password", () -> "unused-signup-test-password");
	}

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired MemberService memberService;
	@MockitoSpyBean MemberRepository memberRepository;

	private static final String PASSWORD = "Signup-test123!";
	// 각 요청에 다른 이메일을 사용합니다. 테스트 종료 시 임시 컨테이너 전체가 제거됩니다.
	// endregion

	// region 정상 가입과 비밀번호 보호
	@Test
	@DisplayName("201 응답과 UUID를 반환하고 DB에는 비밀번호 해시만 저장한다")
	void 회원가입에_성공하면_안전한_가입결과를_반환한다() throws Exception {
		String email = email();
		MvcResult result = mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
						.content(request(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", PASSWORD,
								" 홍길동 ", " 찾음이 ")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.nickname").value("찾음이"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();
		UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertThat(id.version()).isEqualTo(4);
		var row = jdbcTemplate.queryForMap("SELECT * FROM findus.members WHERE id = ?", id);
		String hash = (String) row.get("password_hash");
		assertThat(hash).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
		assertThat(row.get("role")).isEqualTo("USER");
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
		assertThat(row.get("deleted_at")).isNull();
	}

	@Test
	@DisplayName("클라이언트가 관리자 권한·비활성 상태를 보내도 서버 기본값으로 가입한다")
	void 요청으로_관리자_권한을_얻을_수_없다() throws Exception {
		String email = email();
		String body = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
				"name", "홍길동", "nickname", "찾음이", "role", "ADMIN", "status", "INACTIVE"));
		assertThat(signup(body).getResponse().getStatus()).isEqualTo(201);
		var row = jdbcTemplate.queryForMap("SELECT role, status FROM findus.members WHERE email = ?", email);
		assertThat(row.get("role")).isEqualTo("USER");
		assertThat(row.get("status")).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("비밀번호의 앞뒤 공백을 지우지 않고 입력한 그대로 해싱한다")
	void 비밀번호의_공백을_유지한다() throws Exception {
		String email = email();
		String password = " " + PASSWORD + " ";
		assertThat(signup(request(email, password, "이름", "닉네임")).getResponse().getStatus()).isEqualTo(201);
		String hash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM findus.members WHERE email = ?", String.class, email);
		assertThat(passwordEncoder.matches(password, hash)).isTrue();
		assertThat(passwordEncoder.matches(password.strip(), hash)).isFalse();
		assertThat(new MemberSignupRequest(email, password, "이름", "닉네임").toString()).doesNotContain(password);
	}
	// endregion

	// region 중복 이메일과 동시 가입
	@Test
	@DisplayName("같은 이메일은 대소문자·공백이 달라도 409로 거부한다")
	void 중복_이메일은_409를_반환한다() throws Exception {
		String email = email();
		assertThat(signup(request(email, PASSWORD, "이름", "닉네임")).getResponse().getStatus()).isEqualTo(201);
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
						.content(request(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", PASSWORD, "이름", "닉네임")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
		assertThat(count(email)).isEqualTo(1);
	}

	@Test
	@DisplayName("소프트 삭제된 회원의 이메일도 재가입에 사용할 수 없다")
	void 삭제된_이메일의_재가입을_거부한다() throws Exception {
		String email = email();
		signup(request(email, PASSWORD, "이름", "닉네임"));
		jdbcTemplate.update("UPDATE findus.members SET deleted_at = CURRENT_TIMESTAMP, status = 'INACTIVE' WHERE email = ?", email);
		assertThat(signup(request(email, PASSWORD, "이름", "닉네임")).getResponse().getStatus()).isEqualTo(409);
		assertThat(count(email)).isEqualTo(1);
	}

	@Test
	@DisplayName("두 요청이 동시에 중복 검사를 통과해도 한 명만 저장하고 다른 요청은 409를 반환한다")
	void 동시에_가입해도_중복_회원은_저장되지_않는다() throws Exception {
		String email = email();
		CyclicBarrier barrier = new CyclicBarrier(2);
		doAnswer(invocation -> {
			boolean exists = (boolean) invocation.callRealMethod();
			// 두 요청 모두 '가입되지 않음'을 확인한 다음 저장하게 해 DB 제약도 검증합니다.
			barrier.await(10, TimeUnit.SECONDS);
			return exists;
		}).when(memberRepository).existsByEmail(eq(email));
		var pool = Executors.newFixedThreadPool(2);
		try {
			String body = request(email, PASSWORD, "이름", "닉네임");
			var first = pool.submit(() -> signup(body).getResponse().getStatus());
			var second = pool.submit(() -> signup(body).getResponse().getStatus());
			assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(201, 409);
			assertThat(count(email)).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}
	// endregion

	// region 잘못된 입력과 길이 경계
	static Stream<Arguments> invalidInputs() {
		return Stream.of(
				Arguments.of("email", "잘못된이메일"),
				Arguments.of("email", " "),
				Arguments.of("email", "a".repeat(250) + "@example.com"),
				Arguments.of("password", "short"),
				Arguments.of("password", " ".repeat(8)),
				Arguments.of("password", "a".repeat(73)),
				Arguments.of("password", "가".repeat(25)),
				Arguments.of("name", " "),
				Arguments.of("name", "가".repeat(101)),
				Arguments.of("nickname", " "),
				Arguments.of("nickname", "가".repeat(51)));
	}

	@ParameterizedTest(name = "{0} 항목의 잘못된 입력을 거부한다: 사례 {index}")
	@MethodSource("invalidInputs")
	void 잘못된_입력은_400이고_DB에_저장되지_않는다(String field, String value) throws Exception {
		String email = email();
		var values = new java.util.HashMap<>(Map.of("email", email, "password", PASSWORD, "name", "이름", "nickname", "닉네임"));
		values.put(field, value);
		MvcResult result = mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(values)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors[0].field").value(field)).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);
		assertThat(count(email)).isZero();
	}

	@Test
	@DisplayName("필수 항목 누락과 JSON 문법 오류는 400으로 응답한다")
	void 누락된_입력과_깨진_JSON을_거부한다() throws Exception {
		assertThat(signup("{}").getResponse().getStatus()).isEqualTo(400);
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("{broken"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("BCrypt의 정확한 72바이트 경계는 허용한다")
	void 비밀번호_72바이트까지_허용한다() throws Exception {
		String email = email();
		String password = "가".repeat(24); // UTF-8 한글 한 글자는 3바이트입니다.
		assertThat(signup(request(email, password, "이름", "닉네임")).getResponse().getStatus()).isEqualTo(201);
		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM findus.members WHERE email = ?", String.class, email);
		assertThat(passwordEncoder.matches(password, hash)).isTrue();
	}

	@Test
	@DisplayName("컨트롤러를 거치지 않는 서비스 호출도 잘못된 입력을 저장하지 않는다")
	void 서비스에도_입력검증이_적용된다() {
		String email = email();
		assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest(email, "short", "이름", "닉네임")))
				.isInstanceOf(ConstraintViolationException.class);
		assertThat(count(email)).isZero();
	}
	// endregion

	// region 요청 작성과 저장 결과 확인
	private String email() {
		return "signup-" + UUID.randomUUID() + "@example.com";
	}

	private String request(String email, String password, String name, String nickname) {
		return objectMapper.writeValueAsString(Map.of("email", email, "password", password, "name", name, "nickname", nickname));
	}

	private MvcResult signup(String body) throws Exception {
		return mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	private int count(String email) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM findus.members WHERE email = ?", Integer.class, email);
	}
	// endregion
}
