package com.findus.backend.news;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("뉴스 승인·공개·권한 통합 테스트")
class NewsArticleTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","news-test-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "news-test-password");
        p.add("app.news.naver.client-id",() -> "");p.add("app.news.naver.client-secret",() -> "");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD="News-test123!";
    private String admin;

    @BeforeEach void 준비() throws Exception {
        jdbc.update("DELETE FROM findus.news_articles");
        admin=login(member(true));
    }

    @Test void 승인한_기사만_위치에_맞게_공개되고_노출_중단하면_빠진다() throws Exception {
        approve(article("https://news.example.test/a?id=1&x=%26","HOME",null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("장기 실종아동 \"가족\" 찾기"))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-22T00:30:00Z"))
                .andExpect(jsonPath("$.originalUrl").value("https://news.example.test/a?id=1&x=%26"));
        approve(article("https://news.example.test/b","MYPAGE",null)).andExpect(status().isCreated());
        String both=mapper.readTree(approve(article("https://news.example.test/c",null,null)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asString();

        mvc.perform(get("/api/news").param("placement","HOME")).andExpect(status().isOk())
                .andExpect(jsonPath("$",hasSize(2))).andExpect(jsonPath("$[0].note").doesNotExist());
        mvc.perform(get("/api/news").param("placement","MYPAGE")).andExpect(jsonPath("$",hasSize(2)));

        mvc.perform(delete("/api/admin/news/"+both).header("Authorization",admin).with(this::withCsrf)).andExpect(status().isNoContent());
        mvc.perform(get("/api/news").param("placement","HOME")).andExpect(jsonPath("$",hasSize(1)));
        mvc.perform(delete("/api/admin/news/"+both).header("Authorization",admin).with(this::withCsrf)).andExpect(status().isNotFound());
        // 노출 중단한 기사는 다시 승인할 수 있습니다.
        approve(article("https://news.example.test/c",null,null)).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/news").header("Authorization",admin)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].note").value("확인 완료"));
    }

    @Test void 같은_원문은_409이고_잘못된_주소와_지난_종료일은_400() throws Exception {
        approve(article("https://news.example.test/dup",null,null)).andExpect(status().isCreated());
        approve(article("HTTPS://NEWS.example.test/dup#top",null,null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEWS_ALREADY_APPROVED"));
        approve(article("javascript:alert(1)",null,null)).andExpect(status().isBadRequest());
        approve(article("https://user:pw@news.example.test/x",null,null)).andExpect(status().isBadRequest());
        approve(article("https://news.example.test/past",null,OffsetDateTime.now().minusDays(1).toString())).andExpect(status().isBadRequest());
    }

    @Test void 종료일이_지난_기사는_공개_목록에서_제외된다() throws Exception {
        approve(article("https://news.example.test/expiring",null,OffsetDateTime.now().plusDays(1).toString())).andExpect(status().isCreated());
        jdbc.update("UPDATE findus.news_articles SET expires_at=timezone('UTC',now()) - interval '1 minute'");
        mvc.perform(get("/api/news")).andExpect(jsonPath("$",hasSize(0)));
    }

    @Test void 관리자만_관리_API를_쓰고_공개_목록은_누구나_본다() throws Exception {
        String user=login(member(false));
        mvc.perform(get("/api/news")).andExpect(status().isOk());
        mvc.perform(get("/api/news").param("placement","BOTH")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/news")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/news").header("Authorization",user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/news").header("Authorization",admin).contentType("application/json")
                .content(mapper.writeValueAsString(article("https://news.example.test/nocsrf",null,null)))).andExpect(status().isForbidden());
        // 테스트에는 네이버 키가 없으므로 실제 외부 요청 없이 설정 안내를 돌려줍니다.
        mvc.perform(get("/api/admin/news/naver").header("Authorization",admin)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NAVER_NOT_CONFIGURED"));
        mvc.perform(get("/api/admin/news/naver").param("sort","bad").header("Authorization",admin)).andExpect(status().isBadRequest());
    }

    private Map<String,Object> article(String url,String placement,String expiresAt) {
        var body=new HashMap<String,Object>();
        body.put("title","<b>장기 실종아동</b> &quot;가족&quot; 찾기");body.put("originalUrl",url);
        body.put("naverUrl","https://n.news.naver.com/article/1");body.put("pubDate","Thu, 22 Oct 2026 09:30:00 +0900");
        body.put("note","확인 완료");
        if (placement!=null) body.put("placement",placement);
        if (expiresAt!=null) body.put("expiresAt",expiresAt);
        return body;
    }
    private ResultActions approve(Map<String,Object> body) throws Exception {
        return mvc.perform(post("/api/admin/news").header("Authorization",admin).with(this::withCsrf)
                .contentType("application/json").content(mapper.writeValueAsString(body)));
    }
    private Member member(boolean admin) {
        var member=members.save(new Member("news-"+UUID.randomUUID()+"@example.test",encoder.encode(PASSWORD),"가상관리자","가상닉네임"));
        if (admin) jdbc.update("UPDATE findus.members SET role='ADMIN' WHERE id=?",member.getId());
        return member;
    }
    private String login(Member member) throws Exception {
        var result=mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",member.getEmail(),"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        return "Bearer "+mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }
    private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
        try {
            var result=mvc.perform(get("/api/auth/csrf")).andReturn();var csrf=mapper.readTree(result.getResponse().getContentAsString());
            request.addHeader(csrf.get("headerName").asString(),csrf.get("token").asString());request.setCookies(new Cookie[]{result.getResponse().getCookie("XSRF-TOKEN")});return request;
        } catch (Exception e) { throw new IllegalStateException("테스트 CSRF 준비 실패",e); }
    }
}
