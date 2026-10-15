package com.findus.backend.missing;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.findus.backend.missing.service.MissingBatchRunner;
import com.findus.backend.missing.client.MissingPersonSource;
import com.findus.backend.missing.exception.MissingSyncException;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.missing.esntl-id=test+id","app.missing.auth-key=test&key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("실종자 HTTP 수집·배치·동기화 통합 테스트")
class MissingBatchTests {
    // region 임시 DB·Redis와 실제 HTTP 가상 API
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","missing-test-password");
    static final HttpServer SERVER=createServer();
    static volatile String mode="normal";
    static volatile boolean edited=false;
    static volatile CountDownLatch entered,release;
    static final AtomicInteger calls=new AtomicInteger();
    static HttpServer createServer() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/lcm/findChildList.do",exchange -> {
                calls.incrementAndGet();
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if (!body.contains("esntlId=test%2Bid") || !body.contains("authKey=test%26key")) {
                    exchange.sendResponseHeaders(403,-1);exchange.close();return;
                }
                if (mode.equals("slow")) {
                    entered.countDown();
                    try { release.await(10,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                int page=body.contains("page=2") ? 2 : 1;
                int status=mode.equals("serverError") || (mode.equals("partial") && page==2) ? 503 : 200;
                String payload;
                if (mode.equals("auth")) payload="{\"result\":\"99\",\"msg\":\"인증 오류\"}";
                else if (mode.equals("empty")) payload="{\"result\":\"00\",\"totalCount\":0,\"list\":[]}";
                else if (mode.equals("invalid")) payload="not json";
                else {
                    int total=mode.equals("partial") || mode.equals("pages") || mode.equals("drift") ? 101 : 2;
                    if (mode.equals("drift") && page==2) total=102;
                    var rows=new java.util.ArrayList<String>();
                    if (total>=101 && page==1) for (int i=1;i<=100;i++) rows.add(row(i));
                    else if (page==2) rows.add(row(101));
                    else { rows.add(row(1));rows.add(row(mode.equals("duplicate") ? 1 : 2)); }
                    payload="{\"result\":\"00\",\"totalCount\":"+total+",\"list\":["+String.join(",",rows)+"]}";
                }
                byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });
            server.start();return server;
        } catch (Exception e) { throw new IllegalStateException("가상 API 준비 실패",e); }
    }
    static String row(int id) {
        return "{\"msspsnIdntfccd\":"+id+",\"nm\":\"가상회원"+(edited ? "수정" : "")+id+"\",\"writngTrgetDscd\":\"010\",\"sexdstnDscd\":\"여자\",\"occrde\":\"20261001\",\"age\":12,\"ageNow\":\"13\",\"tknphotoFile\":\"가상사진\"}";
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "missing-test-password");
        p.add("app.missing.base-url",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort());
    }
    @Autowired MissingBatchRunner runner;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;
    @Autowired MissingPersonSource source;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @BeforeEach void 초기화() { mode="normal";edited=false;calls.set(0);jdbc.update("DELETE FROM findus.missing_persons"); }
    @AfterAll static void 종료() { SERVER.stop(0); }
    // endregion

    // region 신규 저장·갱신·사진 참조·공개 조회
    @Test void 신규와_변경과_중복을_처리하고_UUID를_유지한다() throws Exception {
        var first=runner.run();assertThat(first.status()).isEqualTo("COMPLETED");assertThat(first.counts().inserted()).isEqualTo(2);
        UUID id=jdbc.queryForObject("SELECT id FROM findus.missing_persons WHERE source_id='1'",UUID.class);
        assertThat(runner.run().counts().updated()).isZero();
        edited=true;assertThat(runner.run().counts().updated()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT id FROM findus.missing_persons WHERE source_id='1'",UUID.class)).isEqualTo(id);
        assertThat(count()).isEqualTo(2);
        mvc.perform(get("/api/missing-persons")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/missing-persons/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("가상회원수정1"))
                .andExpect(jsonPath("$.photoUrl").value("https://www.safe182.go.kr/api/lcm/imgView.do?msspsnIdntfccd=1"))
                .andExpect(jsonPath("$.attribution").value("자료 출처: 경찰청"))
                .andExpect(jsonPath("$.tknphotoFile").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.batch_job_execution WHERE status='COMPLETED'",Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(registry.get("findus.missing.sync.runs").tag("status","COMPLETED").counter().count()).isGreaterThanOrEqualTo(3);
        assertThat(registry.get("findus.missing.sync.duration").tag("status","COMPLETED").timer().count()).isGreaterThanOrEqualTo(3);
        assertThat(registry.get("findus.missing.last.success.timestamp").gauge().value()).isPositive();
    }
    @Test void 모든_페이지를_수집한다() {
        mode="pages";var result=runner.run();assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.counts().received()).isEqualTo(101);assertThat(count()).isEqualTo(101);assertThat(calls.get()).isEqualTo(2);
    }
    @Test void 사라진_정보는_원본을_보존하고_공개_조회에서만_제외한다() throws Exception {
        mode="pages";runner.run();
        UUID removed=jdbc.queryForObject("SELECT id FROM findus.missing_persons WHERE source_id='101'",UUID.class);
        mode="normal";var result=runner.run();assertThat(result.counts().unavailable()).isEqualTo(99);
        assertThat(count()).isEqualTo(101);
        mvc.perform(get("/api/missing-persons")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/missing-persons/"+removed)).andExpect(status().isNotFound());
        mode="pages";runner.run();mvc.perform(get("/api/missing-persons/"+removed)).andExpect(status().isOk());
    }
    // endregion

    // region 불완전한 수집·인증·빈 응답 보호
    @Test void 일부_페이지_실패는_기존_DB를_유지한다() {
        runner.run();mode="partial";var failed=runner.run();assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(count()).isEqualTo(2);assertThat(calls.get()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_persons WHERE source_available",Integer.class)).isEqualTo(2);
        mode="normal";assertThat(runner.run().status()).isEqualTo("COMPLETED");
    }
    @Test void 오류_빈응답_중복_전체건수변경은_반영하지_않는다() {
        runner.run();
        for (String next:List.of("auth","empty","duplicate","drift","invalid")) {
            mode=next;assertThat(runner.run().status()).as(next).isEqualTo("FAILED");assertThat(count()).isEqualTo(2);
        }
    }
    @Test void DB_오류도_전체_롤백한다() {
        runner.run();
        jdbc.execute("ALTER TABLE findus.missing_persons ADD CONSTRAINT ck_test_reject_update CHECK(name NOT LIKE '%수정%')");
        try {
            edited=true;assertThat(runner.run().status()).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT name FROM findus.missing_persons WHERE source_id='1'",String.class)).isEqualTo("가상회원1");
        } finally { jdbc.execute("ALTER TABLE findus.missing_persons DROP CONSTRAINT ck_test_reject_update"); }
    }
    @Test void 동시에_실행하면_한_작업만_수집한다() throws Exception {
        mode="slow";entered=new CountDownLatch(1);release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(runner::run);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(runner::run);assertThat(second.get(10,TimeUnit.SECONDS).status()).isEqualTo("FAILED");
            release.countDown();assertThat(first.get(15,TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");assertThat(calls.get()).isEqualTo(1);
        } finally { release.countDown();pool.shutdownNow(); }
    }
    // endregion

    // region 관리자 권한·CSRF·페이지 조건
    @Test void 일반회원은_배치를_실행할_수_없고_관리자는_실행한다() throws Exception {
        var member=members.save(new Member(UUID.randomUUID()+"@example.test",encoder.encode("Member-test123!"),"테스트","테스트"));
        String token=login(member);
        mvc.perform(post("/api/admin/missing-sync").header("Authorization","Bearer "+token).with(this::withCsrf)).andExpect(status().isForbidden());
        jdbc.update("UPDATE findus.members SET role='ADMIN' WHERE id=?",member.getId());
        token=login(member);
        mvc.perform(post("/api/admin/missing-sync").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/missing-sync").header("Authorization","Bearer "+token).with(this::withCsrf)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/admin/missing-sync").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/missing-sync")).andExpect(status().isUnauthorized());
    }
    @Test void 페이지와_없는_UUID를_검증한다() throws Exception {
        mvc.perform(get("/api/missing-persons?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/missing-persons?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/missing-persons/"+UUID.randomUUID())).andExpect(status().isNotFound());
    }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM findus.missing_persons",Integer.class); }
    private String login(Member member) throws Exception {
        var result=mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",member.getEmail(),"password","Member-test123!"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
        try {
            var result=mvc.perform(get("/api/auth/csrf")).andReturn();var token=mapper.readTree(result.getResponse().getContentAsString());
            request.addHeader(token.get("headerName").asText(),token.get("token").asText());request.setCookies(new Cookie[]{result.getResponse().getCookie("XSRF-TOKEN")});return request;
        } catch (Exception e) { throw new IllegalStateException("테스트 CSRF 준비 실패",e); }
    }
}
