package com.findus.backend.dev;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import com.findus.backend.dev.config.DummyDataStartupRunner;
import com.findus.backend.dev.service.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
        "spring.config.import=", "app.monitoring.enabled=false", "app.health.monitoring.enabled=false",
        "management.server.port=0", "app.dummy.enabled=true", "app.dummy.startup-enabled=true",
        "app.dummy.password=Dummy-test123!"})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
@DisplayName("로컬 더미 데이터 생성·갱신·보존 테스트")
class DummyDataTests {
    // region 개발 DB와 분리한 임시 PostgreSQL·Redis
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
            .withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", "dummy-test-password");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "dummy-test-password");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DummyDataService service;
    @Autowired DummyDataStartupRunner runner;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @BeforeEach
    void 테스트_DB만_초기화한다() {
        jdbc.update("DELETE FROM findus.comments");
        jdbc.update("DELETE FROM findus.posts");
        jdbc.update("DELETE FROM findus.members");
        jdbc.update("DELETE FROM findus.dummy_data_sets");
        runner.run(new DefaultApplicationArguments());
    }
    // endregion

    // region 생성 수·관계·실제 로그인
    @Test
    void 시작_데이터와_고정_데이터가_별도로_생성된다() {
        assertCounts(1, 3, 6);
        var result = service.createPersistent();
        assertThat(result.changed()).isTrue();
        assertThat(result.members()).isEqualTo(5);
        assertThat(result.posts()).isEqualTo(36);
        assertThat(result.comments()).isEqualTo(72);
        assertCounts(6, 39, 78);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT post_id FROM findus.comments GROUP BY post_id HAVING count(*) <> 2) c", Integer.class)).isZero();
        String hash = jdbc.queryForObject("SELECT password_hash FROM findus.members WHERE email='dummy.persistent1@example.test'", String.class);
        assertThat(encoder.matches("Dummy-test123!", hash)).isTrue();
        assertThat(hash).isNotEqualTo("Dummy-test123!");
    }
    @Test
    void 생성된_회원으로_로그인할_수_있다() throws Exception {
        service.createPersistent();
        mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType("application/json")
                .content("{\"email\":\"dummy.persistent1@example.test\",\"password\":\"Dummy-test123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
    }
    // endregion

    // region 재시작·재요청 시 보존 범위
    @Test
    void 고정_데이터는_재요청해도_수정과_삭제를_유지한다() {
        service.createPersistent();
        UUID post = jdbc.queryForObject("SELECT p.id FROM findus.posts p JOIN findus.members m ON m.id=p.member_id WHERE m.email='dummy.persistent1@example.test' LIMIT 1", UUID.class);
        jdbc.update("UPDATE findus.posts SET title='직접 수정',deleted_at=timezone('UTC',now()) WHERE id=?", post);
        assertThat(service.createPersistent().changed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT title FROM findus.posts WHERE id=?", String.class, post)).isEqualTo("직접 수정");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM findus.posts WHERE id=?", Boolean.class, post)).isTrue();
        assertCounts(6,39,78);
    }
    @Test
    void 시작_갱신은_고정_데이터와_수동_댓글을_보존한다() {
        service.createPersistent();
        UUID post = jdbc.queryForObject("SELECT p.id FROM findus.posts p JOIN findus.members m ON m.id=p.member_id WHERE m.email='dummy.startup1@example.test' LIMIT 1", UUID.class);
        UUID member = jdbc.queryForObject("SELECT id FROM findus.members WHERE email='dummy.persistent1@example.test'", UUID.class);
        UUID manual = UUID.randomUUID();
        jdbc.update("INSERT INTO findus.comments(id,post_id,member_id,content,created_at,updated_at) VALUES(?,?,?,'직접 단 댓글',timezone('UTC',now()),timezone('UTC',now()))", manual,post,member);
        jdbc.update("UPDATE findus.posts SET title='시작 데이터 수정',deleted_at=timezone('UTC',now()) WHERE id=?", post);
        jdbc.update("UPDATE findus.posts SET title='고정 데이터 수정' WHERE member_id=?", member);
        var created = jdbc.queryForObject("SELECT created_at FROM findus.posts WHERE id=?", java.time.LocalDateTime.class,post);
        runner.run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT title FROM findus.posts WHERE id=?", String.class,post)).startsWith("[시작갱신 더미]");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM findus.posts WHERE id=?", Boolean.class,post)).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_at FROM findus.posts WHERE id=?", java.time.LocalDateTime.class,post)).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT content FROM findus.comments WHERE id=?", String.class,manual)).isEqualTo("직접 단 댓글");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.posts WHERE title='고정 데이터 수정'",Integer.class)).isEqualTo(8);
        assertCounts(6,39,79);
    }
    @Test
    void 충돌하면_부분_생성하지_않고_전체_롤백한다() {
        jdbc.update("INSERT INTO findus.members(id,email,password_hash,name,nickname,created_at,updated_at) VALUES(?,'dummy.persistent2@example.test','테스트해시','직접가입','직접가입',timezone('UTC',now()),timezone('UTC',now()))",UUID.randomUUID());
        assertThatThrownBy(service::createPersistent).isInstanceOf(DataIntegrityViolationException.class);
        assertCounts(2,3,6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.dummy_data_sets",Integer.class)).isZero();
    }
    @Test
    void 동시에_요청해도_고정_데이터를_한번만_생성한다() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            var a=pool.submit(() -> { barrier.await(); return service.createPersistent().changed(); });
            var b=pool.submit(() -> { barrier.await(); return service.createPersistent().changed(); });
            assertThat(a.get(30,java.util.concurrent.TimeUnit.SECONDS)).isNotEqualTo(b.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertCounts(6,39,78);
        } finally { pool.shutdownNow(); }
    }
    // endregion

    // region 생성 URL과 CSRF·Swagger 안내
    @Test
    void 생성_URL은_POST와_CSRF가_필요하다() throws Exception {
        mvc.perform(post("/api/dev/dummy-data")).andExpect(status().isForbidden());
        assertCounts(1,3,6);
        mvc.perform(post("/api/dev/dummy-data").with(this::withCsrf)).andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.posts").value(36));
        mvc.perform(post("/api/dev/dummy-data").with(this::withCsrf)).andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
        mvc.perform(get("/api/dev/dummy-data")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/dev/dummy-data'].post.summary").value("고정 더미 데이터 생성"));
    }
    // endregion
    private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
        try {
            var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var token = mapper.readTree(result.getResponse().getContentAsString());
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            request.setCookies(new Cookie[]{result.getResponse().getCookie("XSRF-TOKEN")});
            return request;
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 보안 토큰 준비에 실패했습니다.", exception);
        }
    }
    private void assertCounts(int members,int posts,int comments) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.members",Integer.class)).isEqualTo(members);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.posts",Integer.class)).isEqualTo(posts);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.comments",Integer.class)).isEqualTo(comments);
    }
}
