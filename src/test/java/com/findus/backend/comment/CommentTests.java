package com.findus.backend.comment;

import java.time.LocalDateTime;
import com.findus.backend.comment.dto.CommentWriteRequest;
import com.findus.backend.comment.service.CommentService;
import com.findus.backend.comment.exception.CommentNotFoundException;
import jakarta.persistence.EntityManagerFactory;
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

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("댓글 CRUD·페이지·권한·조회 쿼리 통합 테스트")
class CommentTests {
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
	@Autowired PostService posts;
	@Autowired CommentService comments;
	@Autowired EntityManagerFactory entityManagerFactory;
	private static final String PASSWORD = "Post-test123!";

	@BeforeEach
	void 임시_게시글을_초기화한다() {
		// 이 테스트가 만든 임시 컨테이너 안의 데이터만 초기화합니다.
		jdbc.update("DELETE FROM findus.comments");
		jdbc.update("DELETE FROM findus.posts");
		jdbc.update("DELETE FROM findus.members");
	}
	// endregion

	// region 작성·수정·삭제와 공개 정보
	@Test
	@DisplayName("댓글을 저장·수정·소프트 삭제하고 작성자의 공개 정보만 반환한다")
	void 댓글_CRUD를_검증한다() throws Exception {
		var owner = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		String url = url(postId);
		var created = write(post(url), session, "첫 댓글\n  들여쓰기").andExpect(status().isCreated())
				.andExpect(jsonPath("$.postId").value(postId.toString()))
				.andExpect(jsonPath("$.author.id").value(owner.getId().toString()))
				.andExpect(jsonPath("$.author.email").doesNotExist())
				.andExpect(jsonPath("$.author.name").doesNotExist())
				.andExpect(jsonPath("$.author.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.post").doesNotExist()).andReturn();
		UUID id = id(created);
		var before = jdbc.queryForMap("SELECT * FROM findus.comments WHERE id = ?", id);
		assertThat(before.get("post_id")).isEqualTo(postId);
		assertThat(before.get("member_id")).isEqualTo(owner.getId());
		assertThat(before.get("created_at")).isNotNull();
		write(put(url + "/" + id), session, "수정 댓글").andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("수정 댓글"));
		var after = jdbc.queryForMap("SELECT * FROM findus.comments WHERE id = ?", id);
		assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
		assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
		mvc.perform(delete(url + "/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		assertThat(jdbc.queryForMap("SELECT deleted_at FROM findus.comments WHERE id = ?", id).get("deleted_at")).isNotNull();
		mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		write(put(url + "/" + id), session, "복구 시도").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
		mvc.perform(delete(url + "/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("요청에 작성자 ID를 넣어도 실제 로그인 회원이 작성자가 된다")
	void 작성자_위조를_막는다() throws Exception {
		var owner = member();
		var other = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		mvc.perform(post(url(postId)).header("Authorization", session.bearer()).with(this::withCsrf)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
						"content", "댓글", "memberId", other.getId(), "authorId", other.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.author.id").value(owner.getId().toString()));
	}
	// endregion

	// region 작성자 권한·JWT·CSRF
	@Test
	@DisplayName("게시글 작성자나 관리자라도 다른 회원의 댓글을 수정·삭제할 수 없다")
	void 댓글_작성자만_변경할_수_있다() throws Exception {
		var postOwner = member();
		var commentOwner = member();
		UUID postId = posts.create(postOwner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID id = comments.create(postId, commentOwner.getId(), new CommentWriteRequest("원본 댓글")).id();
		jdbc.update("UPDATE findus.members SET role = 'ADMIN' WHERE id = ?", postOwner.getId());
		var session = login(postOwner);
		write(put(url(postId) + "/" + id), session, "무단 수정").andExpect(status().isForbidden());
		mvc.perform(delete(url(postId) + "/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT content FROM findus.comments WHERE id = ?", String.class, id)).isEqualTo("원본 댓글");
	}

	@Test
	@DisplayName("목록은 공개지만 작성·수정·삭제는 JWT와 CSRF가 모두 필요하다")
	void 댓글_쓰기의_인증과_CSRF를_검증한다() throws Exception {
		var owner = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID id = comments.create(postId, owner.getId(), new CommentWriteRequest("댓글")).id();
		mvc.perform(get(url(postId))).andExpect(status().isOk());
		for (var request : List.of(post(url(postId)), put(url(postId) + "/" + id), delete(url(postId) + "/" + id))) {
			mvc.perform(request.with(this::withCsrf).contentType(MediaType.APPLICATION_JSON).content(body("댓글")))
					.andExpect(status().isUnauthorized());
		}
		for (var request : List.of(post(url(postId)), put(url(postId) + "/" + id), delete(url(postId) + "/" + id))) {
			mvc.perform(request.header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON).content(body("댓글")))
					.andExpect(status().isForbidden());
		}
	}
	// endregion

	// region 게시글 범위와 삭제된 데이터
	@Test
	@DisplayName("다른 게시글의 댓글 ID는 404이며 삭제된 게시글에는 댓글을 조회·작성·변경할 수 없다")
	void 댓글이_속한_게시글과_삭제_상태를_확인한다() throws Exception {
		var owner = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID otherPostId = posts.create(owner.getId(), new PostWriteRequest("다른 글", "본문")).id();
		UUID id = comments.create(postId, owner.getId(), new CommentWriteRequest("댓글")).id();
		write(put(url(otherPostId) + "/" + id), session, "잘못된 경로").andExpect(status().isNotFound());
		mvc.perform(delete(url(otherPostId) + "/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNotFound());
		posts.delete(postId, owner.getId());
		mvc.perform(get(url(postId))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
		write(post(url(postId)), session, "새 댓글").andExpect(status().isNotFound());
		write(put(url(postId) + "/" + id), session, "수정").andExpect(status().isNotFound());
		mvc.perform(delete(url(postId) + "/" + id).header("Authorization", session.bearer()).with(this::withCsrf))
				.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.comments WHERE post_id = ?", Integer.class, postId)).isEqualTo(1);
	}

	@Test
	@DisplayName("탈퇴한 작성자의 댓글에는 탈퇴한 회원을 표시하고 개인정보를 공개하지 않는다")
	void 탈퇴_작성자의_공개_응답을_확인한다() throws Exception {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		comments.create(postId, owner.getId(), new CommentWriteRequest("댓글"));
		jdbc.update("UPDATE findus.members SET status = 'INACTIVE', deleted_at = CURRENT_TIMESTAMP WHERE id = ?", owner.getId());
		mvc.perform(get(url(postId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].author.nickname").value("탈퇴한 회원"))
				.andExpect(jsonPath("$.content[0].author.email").doesNotExist());
	}
	// endregion

	// region 페이지·정렬·추가 조회 방지
	@Test
	@DisplayName("작성 순서대로 페이지를 나누고 삭제된 댓글과 다른 게시글 댓글은 제외한다")
	void 게시글별_댓글_페이지를_조회한다() throws Exception {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID other = posts.create(owner.getId(), new PostWriteRequest("다른 글", "본문")).id();
		comments.create(other, owner.getId(), new CommentWriteRequest("다른 게시글 댓글"));
		for (int i = 0; i < 4; i++) {
			UUID id = comments.create(postId, owner.getId(), new CommentWriteRequest("댓글" + i)).id();
			jdbc.update("UPDATE findus.comments SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 10, 1, 0, 0).plusSeconds(i), id);
			if (i == 1) comments.delete(postId, id, owner.getId());
		}
		mvc.perform(get(url(postId)).param("size", "2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].content").value("댓글0"))
				.andExpect(jsonPath("$.content[1].content").value("댓글2"))
				.andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.hasNext").value(true));
		mvc.perform(get(url(postId)).param("page", "1").param("size", "2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].content").value("댓글3")).andExpect(jsonPath("$.hasPrevious").value(true));
		mvc.perform(get(url(postId)).param("size", "1000")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
		mvc.perform(get(url(postId)).param("sort", "author.email,asc")).andExpect(status().isBadRequest());
		mvc.perform(get(url(postId)).param("page", "2147483647").param("size", "100")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("작성 시간이 같은 댓글은 페이지 간 중복이 없고 작성자 조회 쿼리가 늘어나지 않는다")
	void 정렬과_조회_쿼리를_확인한다() throws Exception {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		for (int i = 0; i < 5; i++) comments.create(postId, member().getId(), new CommentWriteRequest("댓글" + i));
		jdbc.update("UPDATE findus.comments SET created_at = TIMESTAMP '2026-10-01 00:00:00'");
		var ids = new java.util.HashSet<String>();
		for (int page = 0; page < 5; page++) {
			var result = mvc.perform(get(url(postId)).param("size", "1").param("page", Integer.toString(page)))
					.andExpect(status().isOk()).andReturn();
			ids.add(mapper.readTree(result.getResponse().getContentAsString()).at("/content/0/id").asText());
		}
		assertThat(ids).hasSize(5);
		var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
		statistics.clear();
		mvc.perform(get(url(postId)).param("size", "3")).andExpect(status().isOk());
		// 게시글 확인·댓글과 작성자 조회·전체 개수 조회의 최대 3개 쿼리만 사용합니다.
		assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 3L);
	}
	// endregion

	// region 입력·DB 제약조건
	static Stream<Arguments> invalidInputs() {
		return Stream.of(Arguments.of(" "), Arguments.of("\n\t"), Arguments.of("가".repeat(1001)));
	}

	@ParameterizedTest(name = "잘못된 댓글 입력을 거부한다: 사례 {index}")
	@MethodSource("invalidInputs")
	void 잘못된_댓글을_저장하지_않는다(String value) throws Exception {
		var owner = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID id = comments.create(postId, owner.getId(), new CommentWriteRequest("원본 댓글")).id();
		write(post(url(postId)), session, value).andExpect(status().isBadRequest());
		write(put(url(postId) + "/" + id), session, value).andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT content FROM findus.comments WHERE id = ?", String.class, id)).isEqualTo("원본 댓글");
	}

	@Test
	@DisplayName("최대 길이 1000자는 허용하고 누락된 입력·잘못된 UUID·없는 게시글을 구분한다")
	void 길이_경계와_잘못된_요청을_검증한다() throws Exception {
		var owner = member();
		var session = login(owner);
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		write(post(url(postId)), session, "가".repeat(1000)).andExpect(status().isCreated());
		for (String body : List.of("{}", "{broken", "{\"content\":null}")) {
			mvc.perform(post(url(postId)).header("Authorization", session.bearer()).with(this::withCsrf)
					.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/posts/not-a-uuid/comments")).andExpect(status().isBadRequest());
		mvc.perform(get(url(UUID.randomUUID()))).andExpect(status().isNotFound());
		assertThatThrownBy(() -> comments.create(postId, owner.getId(), new CommentWriteRequest(" ")))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> comments.findAll(postId, PageRequest.of(0, 101)))
				.isInstanceOf(com.findus.backend.comment.exception.CommentPageException.class);
	}

	@Test
	@DisplayName("DB는 없는 게시글·회원 참조와 빈 댓글 저장을 거부한다")
	void 댓글_DB제약조건을_확인한다() {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		String sql = "INSERT INTO findus.comments(id,post_id,member_id,content,created_at,updated_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
		for (Object[] values : List.of(new Object[]{UUID.randomUUID(), UUID.randomUUID(), owner.getId(), "댓글"},
				new Object[]{UUID.randomUUID(), postId, UUID.randomUUID(), "댓글"},
				new Object[]{UUID.randomUUID(), postId, owner.getId(), "\n\t"})) {
			assertThatThrownBy(() -> jdbc.update(sql, values)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		}
	}
	// endregion

	// region 동시에 들어오는 삭제·수정·작성
	@Test
	@DisplayName("댓글 수정·삭제가 겹쳐도 삭제된 댓글이 다시 조회되지 않는다")
	void 동시_수정으로_삭제를_되돌릴_수_없다() throws Exception {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		UUID id = comments.create(postId, owner.getId(), new CommentWriteRequest("댓글")).id();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			var barrier = new java.util.concurrent.CyclicBarrier(2);
			var update = pool.submit(() -> {
				barrier.await();
				try { comments.update(postId, id, owner.getId(), new CommentWriteRequest("수정")); }
				catch (CommentNotFoundException expected) { /* 삭제가 먼저 처리되면 수정 거부가 정상입니다. */ }
				return null;
			});
			var delete = pool.submit(() -> { barrier.await(); comments.delete(postId, id, owner.getId()); return null; });
			update.get(15, java.util.concurrent.TimeUnit.SECONDS);
			delete.get(15, java.util.concurrent.TimeUnit.SECONDS);
			assertThat(comments.findAll(postId, PageRequest.of(0, 10)).totalElements()).isZero();
			assertThat(jdbc.queryForMap("SELECT deleted_at FROM findus.comments WHERE id = ?", id).get("deleted_at")).isNotNull();
		} finally { pool.shutdownNow(); }
	}

	@Test
	@DisplayName("게시글 삭제와 댓글 작성이 겹쳐도 삭제된 게시글의 댓글은 공개되지 않는다")
	void 게시글_삭제와_댓글_작성을_조정한다() throws Exception {
		var owner = member();
		UUID postId = posts.create(owner.getId(), new PostWriteRequest("게시글", "본문")).id();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			var barrier = new java.util.concurrent.CyclicBarrier(2);
			var create = pool.submit(() -> {
				barrier.await();
				try { comments.create(postId, owner.getId(), new CommentWriteRequest("동시 댓글")); }
				catch (com.findus.backend.post.exception.PostNotFoundException expected) { /* 게시글 삭제가 먼저면 작성 거부가 정상입니다. */ }
				return null;
			});
			var delete = pool.submit(() -> { barrier.await(); posts.delete(postId, owner.getId()); return null; });
			create.get(15, java.util.concurrent.TimeUnit.SECONDS);
			delete.get(15, java.util.concurrent.TimeUnit.SECONDS);
			assertThatThrownBy(() -> comments.findAll(postId, PageRequest.of(0, 10)))
					.isInstanceOf(com.findus.backend.post.exception.PostNotFoundException.class);
			assertThatThrownBy(() -> comments.create(postId, owner.getId(), new CommentWriteRequest("삭제 후 댓글")))
					.isInstanceOf(com.findus.backend.post.exception.PostNotFoundException.class);
		} finally { pool.shutdownNow(); }
	}
	// endregion

	private String url(UUID postId) { return "/api/posts/" + postId + "/comments"; }

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
			Session session, String content) throws Exception {
		return mvc.perform(request.header("Authorization", session.bearer()).with(this::withCsrf)
				.contentType(MediaType.APPLICATION_JSON).content(body(content)));
	}

	private String body(String content) { return mapper.writeValueAsString(Map.of("content", content)); }
	private UUID id(MvcResult result) throws Exception {
		return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}
	private record Session(String access) {
		String bearer() { return "Bearer " + access; }
		@Override public String toString() { return "Session[내용 생략]"; }
	}
	// endregion
}
