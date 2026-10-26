package com.findus.backend.post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.post.dto.PostWriteRequest;
import com.findus.backend.post.service.PostService;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("게시글 CRUD·페이지·권한 통합 테스트")
class PostTests {
	// region 실제 개발 DB와 분리된 테스트 환경
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");
	@Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379).withCommand("redis-server", "--requirepass", "post-test-password");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "post-test-password");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired MemberRepository members;
	@Autowired PostService service;
	private static final String PASSWORD = "Post-test123!";

	@BeforeEach
	void 임시_게시글을_초기화한다() {
		// 이 테스트가 만든 임시 컨테이너 안의 데이터만 초기화합니다.
		jdbc.update("DELETE FROM findus.posts");
		jdbc.update("DELETE FROM findus.members");
	}
	// endregion

	// region 작성·상세 조회·수정·소프트 삭제
	@Test
	@DisplayName("작성부터 수정·삭제까지 처리하고 DB 행은 유지하되 공개 조회에서 제외한다")
	void 게시글_CRUD와_소프트삭제를_검증한다() throws Exception {
		var owner = member();
		var session = login(owner);
		var created = write(post("/api/posts"), session, " 첫 게시글 ", "첫 본문\n  들여쓰기")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("첫 게시글"))
				.andExpect(jsonPath("$.author.id").value(owner.getId().toString()))
				.andExpect(jsonPath("$.author.nickname").value(owner.getNickname()))
				.andExpect(jsonPath("$.author.email").doesNotExist())
				.andExpect(jsonPath("$.author.passwordHash").doesNotExist()).andReturn();
		UUID id = id(created);
		assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/posts/" + id);
		var before = jdbc.queryForMap("SELECT * FROM findus.posts WHERE id = ?", id);
		assertThat(before.get("member_id")).isEqualTo(owner.getId());
		assertThat(before.get("created_at")).isNotNull();
		assertThat(before.get("deleted_at")).isNull();
		mvc.perform(get("/api/posts/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("첫 본문\n  들여쓰기"));
		var updated = write(put("/api/posts/" + id), session, "수정 제목", "수정 본문")
				.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("수정 제목"))
				.andExpect(jsonPath("$.content").value("수정 본문")).andReturn();
		var after = jdbc.queryForMap("SELECT * FROM findus.posts WHERE id = ?", id);
		assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
		assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
		assertThat(LocalDateTime.parse(mapper.readTree(updated.getResponse().getContentAsString())
				.get("updatedAt").asText())).isCloseTo(((java.sql.Timestamp) after.get("updated_at")).toLocalDateTime(),
						org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
		mvc.perform(delete("/api/posts/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.posts WHERE id = ?", Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT deleted_at FROM findus.posts WHERE id = ?", id).get("deleted_at")).isNotNull();
		mvc.perform(get("/api/posts/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
		mvc.perform(get("/api/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		write(put("/api/posts/" + id), session, "복구 시도", "본문").andExpect(status().isNotFound());
		mvc.perform(delete("/api/posts/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("작성자 ID를 요청에 끼워 넣어도 실제 로그인한 회원이 작성자가 된다")
	void 작성자를_위조할_수_없다() throws Exception {
		var owner = member();
		var other = member();
		var session = login(owner);
		mvc.perform(post("/api/posts").header("Authorization", session.bearer()).with(this::withCsrf)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
						"title", "작성자 확인", "content", "본문", "memberId", other.getId(), "authorId", other.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.author.id").value(owner.getId().toString()));
	}
	// endregion

	// region 인증·작성자 권한·CSRF
	@Test
	@DisplayName("다른 회원은 관리자여도 게시글을 수정하거나 삭제할 수 없다")
	void 작성자만_수정하고_삭제한다() throws Exception {
		var owner = member();
		UUID id = service.create(owner.getId(), new PostWriteRequest("원본 제목", "원본 본문")).id();
		var other = member();
		jdbc.update("UPDATE findus.members SET role = 'ADMIN' WHERE id = ?", other.getId());
		var session = login(other);
		write(put("/api/posts/" + id), session, "수정 시도", "내용").andExpect(status().isForbidden());
		mvc.perform(delete("/api/posts/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isForbidden());
		var row = jdbc.queryForMap("SELECT title, deleted_at FROM findus.posts WHERE id = ?", id);
		assertThat(row.get("title")).isEqualTo("원본 제목");
		assertThat(row.get("deleted_at")).isNull();
	}

	@Test
	@DisplayName("조회는 공개하지만 작성·수정·삭제는 인증과 CSRF가 필요하다")
	void 읽기만_공개하고_쓰기_요청을_보호한다() throws Exception {
		var owner = member();
		var session = login(owner);
		UUID id = service.create(owner.getId(), new PostWriteRequest("보호 대상", "본문")).id();
		mvc.perform(get("/api/posts")).andExpect(status().isOk());
		mvc.perform(get("/api/posts/" + id)).andExpect(status().isOk());
		for (var request : List.of(post("/api/posts"), put("/api/posts/" + id), delete("/api/posts/" + id))) {
			mvc.perform(request.with(this::withCsrf).contentType(MediaType.APPLICATION_JSON)
					.content(body("무단 요청", "본문"))).andExpect(status().isUnauthorized());
		}
		for (var request : List.of(post("/api/posts"), put("/api/posts/" + id), delete("/api/posts/" + id))) {
			mvc.perform(request.header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON)
					.content(body("보안 토큰 누락", "본문"))).andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.posts", Integer.class)).isEqualTo(1);
		assertThat(service.findById(id).title()).isEqualTo("보호 대상");
	}

	@Test
	@DisplayName("React에서 PUT·DELETE를 요청할 수 있고 미허용 출처는 차단한다")
	void 게시글_변경의_CORS를_검증한다() throws Exception {
		for (String method : List.of("PUT", "DELETE")) {
			mvc.perform(options("/api/posts/" + UUID.randomUUID()).header("Origin", "http://localhost:5173")
					.header("Access-Control-Request-Method", method)
					.header("Access-Control-Request-Headers", "authorization,content-type,x-xsrf-token"))
					.andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		}
		mvc.perform(options("/api/posts").header("Origin", "https://unknown.example")
				.header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
	}
	// endregion

	@Test
	@DisplayName("수정과 삭제가 동시에 요청되어도 삭제된 글이 다시 조회되지 않는다")
	void 동시_수정으로_삭제_상태가_되돌아가지_않는다() throws Exception {
		var owner = member();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			for (int i = 0; i < 5; i++) {
				UUID id = service.create(owner.getId(), new PostWriteRequest("동시 처리", "본문")).id();
				var barrier = new java.util.concurrent.CyclicBarrier(2);
				var update = pool.submit(() -> {
					barrier.await();
					try {
						service.update(id, owner.getId(), new PostWriteRequest("수정 내용", "본문"));
						return true;
					} catch (com.findus.backend.post.exception.PostNotFoundException exception) {
						// 삭제가 먼저 처리됐다면 수정이 거부되는 것이 정상입니다.
						return false;
					}
				});
				var delete = pool.submit(() -> {
					barrier.await();
					service.delete(id, owner.getId());
					return null;
				});
				update.get(15, java.util.concurrent.TimeUnit.SECONDS);
				delete.get(15, java.util.concurrent.TimeUnit.SECONDS);
				assertThat(jdbc.queryForMap("SELECT deleted_at FROM findus.posts WHERE id = ?", id).get("deleted_at")).isNotNull();
				assertThatThrownBy(() -> service.findById(id)).isInstanceOf(com.findus.backend.post.exception.PostNotFoundException.class);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	// region 페이지 처리와 응답 크기
	@Test
	@DisplayName("최신순으로 페이지를 나누고 삭제된 글은 목록·전체 개수에서 제외한다")
	void 삭제를_제외한_페이지를_반환한다() throws Exception {
		var owner = member();
		for (int i = 0; i < 4; i++) {
			var result = service.create(owner.getId(), new PostWriteRequest("게시글" + i, "본문"));
			jdbc.update("UPDATE findus.posts SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 10, 1, 0, 0).plusSeconds(i), result.id());
			if (i == 2) service.delete(result.id(), owner.getId());
		}
		mvc.perform(get("/api/posts").param("page", "0").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].title").value("게시글3"))
				.andExpect(jsonPath("$.content[1].title").value("게시글1"))
				.andExpect(jsonPath("$.content[0].content").doesNotExist())
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.hasPrevious").value(false));
		mvc.perform(get("/api/posts").param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].title").value("게시글0"))
				.andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.hasPrevious").value(true));
		mvc.perform(get("/api/posts").param("page", "2").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	@DisplayName("작성 시간이 같은 게시글도 페이지 간 중복되지 않고 허용된 정렬만 사용한다")
	void 동일_시간의_정렬을_고정한다() throws Exception {
		var owner = member();
		for (String title : List.of("다", "가", "나")) service.create(owner.getId(), new PostWriteRequest(title, "본문"));
		jdbc.update("UPDATE findus.posts SET created_at = TIMESTAMP '2026-10-01 00:00:00'");
		var ids = new java.util.HashSet<String>();
		for (int page = 0; page < 3; page++) {
			var result = mvc.perform(get("/api/posts").param("size", "1").param("page", Integer.toString(page)))
					.andExpect(status().isOk()).andReturn();
			ids.add(mapper.readTree(result.getResponse().getContentAsString()).get("content").get(0).get("id").asText());
		}
		assertThat(ids).hasSize(3);
		mvc.perform(get("/api/posts").param("sort", "title,asc")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].title").value("가"));
		mvc.perform(get("/api/posts").param("sort", "author.email,asc"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(get("/api/posts").param("page", "2147483647").param("size", "100"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("기본 10개·최대 100개를 적용하고 빈 목록도 페이지 정보와 함께 반환한다")
	void 페이지_기본값과_최대_개수를_적용한다() throws Exception {
		mvc.perform(get("/api/posts")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0)).andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
		mvc.perform(get("/api/posts").param("size", "1000")).andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}
	// endregion

	// region 입력 검증과 없는 게시글
	static Stream<Arguments> invalidInputs() {
		return Stream.of(Arguments.of(" ", "본문"), Arguments.of("제목", " "),
				Arguments.of("가".repeat(201), "본문"), Arguments.of("제목", "가".repeat(10001)));
	}

	@ParameterizedTest(name = "잘못된 제목·본문을 거부한다: 사례 {index}")
	@MethodSource("invalidInputs")
	void 잘못된_작성과_수정을_저장하지_않는다(String title, String content) throws Exception {
		var owner = member();
		var session = login(owner);
		UUID id = service.create(owner.getId(), new PostWriteRequest("원본", "본문")).id();
		write(post("/api/posts"), session, title, content).andExpect(status().isBadRequest());
		write(put("/api/posts/" + id), session, title, content).andExpect(status().isBadRequest());
		assertThat(service.findById(id).title()).isEqualTo("원본");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.posts", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("누락된 본문과 깨진 JSON·UUID는 400이고 없는 글은 404이다")
	void 잘못된_요청과_없는_글을_구분한다() throws Exception {
		var session = login(member());
		for (String body : List.of("{}", "{broken")) {
			mvc.perform(post("/api/posts").header("Authorization", session.bearer()).with(this::withCsrf)
					.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/posts/not-a-uuid")).andExpect(status().isBadRequest());
		UUID missing = UUID.randomUUID();
		mvc.perform(get("/api/posts/" + missing)).andExpect(status().isNotFound());
		write(put("/api/posts/" + missing), session, "제목", "본문").andExpect(status().isNotFound());
		mvc.perform(delete("/api/posts/" + missing).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("최대 길이는 허용하고 서비스 직접 호출에서도 잘못된 입력을 거부한다")
	void 길이_경계와_서비스_검증을_확인한다() {
		var owner = member();
		var result = service.create(owner.getId(), new PostWriteRequest("가".repeat(200), "가".repeat(10000)));
		assertThat(result.title()).hasSize(200);
		assertThat(result.content()).hasSize(10000);
		assertThatThrownBy(() -> service.create(owner.getId(), new PostWriteRequest(" ", "본문")))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.findAll(PageRequest.of(0, 101)))
				.isInstanceOf(com.findus.backend.post.exception.PostPageException.class);
	}
	// endregion

	// region 테스트 요청 보조 도구
	private Member member() {
		return members.save(new Member("post-" + UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD),
				"테스트회원", "게시판확인"));
	}

	private Session login(Member member) throws Exception {
		var result = mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", member.getEmail(), "password", PASSWORD))))
				.andExpect(status().isOk()).andReturn();
		return new Session(mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText());
	}

	private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
		try {
			var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
			var csrf = mapper.readTree(result.getResponse().getContentAsString());
			request.addHeader(csrf.get("headerName").asText(), csrf.get("token").asText());
			request.setCookies(new Cookie[]{result.getResponse().getCookie("XSRF-TOKEN")});
			return request;
		} catch (Exception exception) {
			throw new IllegalStateException("테스트 보안 토큰 준비에 실패했습니다.", exception);
		}
	}

	private org.springframework.test.web.servlet.ResultActions write(MockHttpServletRequestBuilder request,
			Session session, String title, String content) throws Exception {
		return mvc.perform(request.header("Authorization", session.bearer()).with(this::withCsrf)
				.contentType(MediaType.APPLICATION_JSON).content(body(title, content)));
	}

	private String body(String title, String content) { return mapper.writeValueAsString(Map.of("title", title, "content", content)); }
	private UUID id(MvcResult result) throws Exception {
		return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}
	private record Session(String access) {
		String bearer() { return "Bearer " + access; }
		@Override public String toString() { return "Session[내용 생략]"; }
	}
	// endregion
}
