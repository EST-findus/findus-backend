package com.findus.backend.favorite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.findus.backend.favorite.dto.FavoriteAvailability;
import com.findus.backend.favorite.service.MemberFavoriteService;
import com.findus.backend.favorite.exception.FavoritePageException;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.missing.client.MissingPersonSource;
import com.findus.backend.missing.dto.SourcePerson;
import com.findus.backend.missing.service.MissingSyncService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(MemberFavoriteTests.SourceConfig.class)
@DisplayName("관심아동 등록·취소·보안·공개 중단·동시 요청 통합 테스트")
class MemberFavoriteTests {
    // region 개발 DB·실제 경찰청 API와 분리된 테스트 환경
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","favorite-test-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "favorite-test-password");
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class SourceConfig {
        @Bean @Primary FixtureSource fixtureSource() { return new FixtureSource(); }
    }
    static class FixtureSource implements MissingPersonSource {
        List<SourcePerson> rows=List.of();
        @Override public List<SourcePerson> fetchAll() { return rows; }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @Autowired MemberFavoriteService favorites;
    @Autowired FixtureSource fixture;
    @Autowired MissingSyncService sync;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private static final String BASE="/api/members/me/favorites";
    private static final String PASSWORD="Favorite-test123!";
    private final AtomicInteger sourceIds=new AtomicInteger();
    @BeforeEach void 테스트_DB_정리() {
        jdbc.update("DELETE FROM findus.member_favorites");jdbc.update("DELETE FROM findus.missing_persons");
        jdbc.update("DELETE FROM findus.members");fixture.rows=List.of();sourceIds.set(0);
    }
    // endregion

    // region 등록·취소·복원·대상 제한
    @Test void 관심을_등록하고_반복_요청해도_UUID와_등록시각을_유지한다() throws Exception {
        var member=member();String token=login(member);UUID child=child("010");
        var first=register(token,child).andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.child.ageNow").value(35)).andExpect(jsonPath("$.child.category").value("010")).andReturn();
        var before=mapper.readTree(first.getResponse().getContentAsString());
        register(token,child).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(before.get("id").asText()));
        mvc.perform(get(BASE).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        cancel(token,child).andExpect(status().isNoContent());cancel(token,child).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM findus.member_favorites",Boolean.class)).isTrue();
        mvc.perform(get(BASE).header("Authorization",token)).andExpect(jsonPath("$.totalElements").value(0));
        register(token,child).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(before.get("id").asText()))
                .andExpect(jsonPath("$.savedAt").value(before.get("savedAt").asText()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.member_favorites",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM findus.member_favorites",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.members",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_persons",Integer.class)).isEqualTo(1);
    }
    @Test void 아동_이외_분류와_공개중단과_삭제된_대상을_거절한다() throws Exception {
        String token=login(member());
        for (String code:List.of("020","060","061","062","070","080"))
            register(token,child(code)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FAVORITE_TARGET"));
        UUID hidden=child("010");jdbc.update("UPDATE findus.missing_persons SET source_available=false WHERE id=?",hidden);
        register(token,hidden).andExpect(status().isNotFound());
        UUID deleted=child("010");jdbc.update("UPDATE findus.missing_persons SET deleted_at=timezone('UTC',now()) WHERE id=?",deleted);
        register(token,deleted).andExpect(status().isNotFound());register(token,UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.member_favorites",Integer.class)).isZero();
    }
    @Test void 공개중단과_삭제와_분류변경은_개인정보를_숨기며_취소는_허용한다() throws Exception {
        var member=member();String token=login(member);UUID child=child("010");
        register(token,child).andExpect(status().isOk());
        jdbc.update("UPDATE findus.missing_persons SET source_available=false WHERE id=?",child);
        assertHidden(token,"UNAVAILABLE");register(token,child).andExpect(status().isNotFound());
        jdbc.update("UPDATE findus.missing_persons SET source_available=true,category='070' WHERE id=?",child);
        assertHidden(token,"NOT_CHILD");register(token,child).andExpect(status().isBadRequest());
        jdbc.update("UPDATE findus.missing_persons SET category='010',deleted_at=timezone('UTC',now()) WHERE id=?",child);
        assertHidden(token,"UNAVAILABLE");cancel(token,child).andExpect(status().isNoContent());
    }
    // endregion

    // region 인증·CSRF·내 목록 격리
    @Test void JWT와_CSRF가_필요하다() throws Exception {
        UUID child=child("010");String token=login(member());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE+"/"+child).with(this::withCsrf)).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE+"/"+child).with(this::withCsrf)).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE+"/"+child).header("Authorization",token)).andExpect(status().isForbidden());
        register(token,child).andExpect(status().isOk());
        mvc.perform(delete(BASE+"/"+child).header("Authorization",token)).andExpect(status().isForbidden());
    }
    @Test void 다른_회원의_관심을_조회하거나_취소하지_못한다() throws Exception {
        var owner=member();var other=member();String a=login(owner),b=login(other);UUID child=child("010");
        register(a,child).andExpect(status().isOk());
        mvc.perform(get(BASE+"?memberId="+owner.getId()).header("Authorization",b)).andExpect(jsonPath("$.totalElements").value(0));
        cancel(b,child).andExpect(status().isNoContent());
        mvc.perform(get(BASE).header("Authorization",a)).andExpect(jsonPath("$.totalElements").value(1));
        register(b,child).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.member_favorites",Integer.class)).isEqualTo(2);
    }
    @Test void 비활성_탈퇴_회원은_사용할_수_없다() throws Exception {
        var member=member();String token=login(member);UUID child=child("010");
        jdbc.update("UPDATE findus.members SET status='INACTIVE' WHERE id=?",member.getId());
        mvc.perform(get(BASE).header("Authorization",token)).andExpect(status().isUnauthorized());
        register(token,child).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> favorites.register(member.getId(),child)).isInstanceOf(com.findus.backend.auth.exception.AuthException.class);
        jdbc.update("UPDATE findus.members SET deleted_at=timezone('UTC',now()) WHERE id=?",member.getId());
        assertThatThrownBy(() -> favorites.cancel(member.getId(),child)).isInstanceOf(com.findus.backend.auth.exception.AuthException.class);
    }
    // endregion

    // region 페이지·쿼리 개수·잘못된 요청
    @Test void 페이지는_취소된_항목과_다른_회원의_관심을_제외한다() {
        var a=member();var b=member();
        for (int i=0;i<4;i++) favorites.register(a.getId(),child("010"));
        UUID cancelled=jdbc.queryForObject("SELECT missing_person_id FROM findus.member_favorites WHERE member_id=? LIMIT 1",UUID.class,a.getId());
        favorites.cancel(a.getId(),cancelled);favorites.register(b.getId(),child("010"));
        var first=favorites.findMine(a.getId(),PageRequest.of(0,2));var second=favorites.findMine(a.getId(),PageRequest.of(1,2));
        assertThat(first.totalElements()).isEqualTo(3);assertThat(first.content()).hasSize(2);assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).hasSize(1);assertThat(second.hasPrevious()).isTrue();
        assertThat(first.content()).extracting(f -> f.missingPersonId()).doesNotContain(cancelled);
    }
    @Test void 동률_정렬과_다른_아동_목록에서도_쿼리가_늘어나지_않는다() {
        var member=member();for (int i=0;i<5;i++) favorites.register(member.getId(),child("010"));
        jdbc.update("UPDATE findus.member_favorites SET created_at='2026-10-01 00:00:00'");
        var ids=new java.util.HashSet<UUID>();
        for (int page=0;page<3;page++) favorites.findMine(member.getId(),PageRequest.of(page,2)).content().forEach(f -> assertThat(ids.add(f.id())).isTrue());
        assertThat(ids).hasSize(5);
        var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.clear();
        favorites.findMine(member.getId(),PageRequest.of(0,5));
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L,3L);
    }
    @Test void 잘못된_정렬_크기_UUID를_거절한다() throws Exception {
        var member=member();String token=login(member);
        mvc.perform(get(BASE+"?sort=missingPerson.name,asc").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"?page=2147483647&size=100").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(put(BASE+"/not-uuid").header("Authorization",token).with(this::withCsrf)).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> favorites.findMine(member.getId(),PageRequest.of(0,101))).isInstanceOf(FavoritePageException.class);
        assertThatThrownBy(() -> favorites.findMine(member.getId(),Pageable.unpaged())).isInstanceOf(FavoritePageException.class);
        assertThatThrownBy(() -> favorites.register(null,UUID.randomUUID())).isInstanceOf(ConstraintViolationException.class);
    }
    // endregion

    // region 배치 동기화와 DB 제약조건·동시 요청
    @Test void 배치_갱신과_공개복구_후에도_같은_관심_연결을_유지한다() {
        var member=member();UUID child=child("010");
        String source=jdbc.queryForObject("SELECT source_id FROM findus.missing_persons WHERE id=?",String.class,child);
        var saved=favorites.register(member.getId(),child);
        fixture.rows=List.of(row(source,"수정된 가상아동","010"));sync.sync();
        var updated=favorites.findMine(member.getId(),PageRequest.of(0,10)).content().get(0);
        assertThat(updated.id()).isEqualTo(saved.id());assertThat(updated.child().id()).isEqualTo(child);assertThat(updated.child().name()).isEqualTo("수정된 가상아동");
        fixture.rows=List.of(row("99999","다른 가상아동","010"));sync.sync();
        assertThat(favorites.findMine(member.getId(),PageRequest.of(0,10)).content().get(0).child()).isNull();
        fixture.rows=List.of(row(source,"다시 공개된 아동","010"));sync.sync();
        var restored=favorites.findMine(member.getId(),PageRequest.of(0,10)).content().get(0);
        assertThat(restored.id()).isEqualTo(saved.id());assertThat(restored.child().id()).isEqualTo(child);
    }
    @Test void DB도_중복_관심과_없는_부모를_거절한다() {
        var member=member();UUID child=child("010");favorites.register(member.getId(),child);
        String sql="INSERT INTO findus.member_favorites(id,member_id,missing_person_id,created_at,updated_at) VALUES(?,?,?,timezone('UTC',now()),timezone('UTC',now()))";
        assertThatThrownBy(() -> jdbc.update(sql,UUID.randomUUID(),member.getId(),child)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql,UUID.randomUUID(),member.getId(),UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql,UUID.randomUUID(),UUID.randomUUID(),child)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void 배치가_공개중단을_반영중이면_기다린뒤_등록을_거절한다() throws Exception {
        var member=member();UUID child=child("010");
        var changed=new java.util.concurrent.CountDownLatch(1);
        var commit=new java.util.concurrent.CountDownLatch(1);
        var started=new java.util.concurrent.CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(() -> {
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.update("UPDATE findus.missing_persons SET source_available=false WHERE id=?",child);
                    changed.countDown();
                    try { if (!commit.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("테스트 트랜잭션 대기 시간 초과"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                });
            });
            assertThat(changed.await(10,TimeUnit.SECONDS)).isTrue();
            var registration=pool.submit(() -> { started.countDown();return favorites.register(member.getId(),child); });
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> registration.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            commit.countDown();writer.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> registration.get(10,TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(com.findus.backend.favorite.exception.FavoriteNotFoundException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.member_favorites",Integer.class)).isZero();
        } finally { commit.countDown();pool.shutdownNow(); }
    }
    @Test void 최초_동시_등록도_한_행이고_취소와_등록의_경합도_정합성을_유지한다() throws Exception {
        var member=member();UUID child=child("010");var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CyclicBarrier(2);
            var a=pool.submit(() -> { start.await();return favorites.register(member.getId(),child).id(); });
            var b=pool.submit(() -> { start.await();return favorites.register(member.getId(),child).id(); });
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            var again=new CyclicBarrier(2);
            var c=pool.submit(() -> { again.await();favorites.cancel(member.getId(),child);return true; });
            var d=pool.submit(() -> { again.await();favorites.register(member.getId(),child);return true; });
            c.get(15,TimeUnit.SECONDS);d.get(15,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.member_favorites",Integer.class)).isEqualTo(1);
            // 동시 요청 중 어느 쪽이 먼저였든 이후 순차 요청으로 정상 복원·취소할 수 있어야 합니다.
            favorites.register(member.getId(),child);assertThat(favorites.findMine(member.getId(),PageRequest.of(0,10)).totalElements()).isEqualTo(1);
            favorites.cancel(member.getId(),child);assertThat(favorites.findMine(member.getId(),PageRequest.of(0,10)).totalElements()).isZero();
        } finally { pool.shutdownNow(); }
    }
    // endregion

    // region 테스트 도우미
    private Member member() { return members.save(new Member("favorite-"+UUID.randomUUID()+"@example.test",encoder.encode(PASSWORD),"가상회원","가상닉네임")); }
    private UUID child(String category) {
        UUID id=UUID.randomUUID();String source=String.valueOf(sourceIds.incrementAndGet());
        jdbc.update("INSERT INTO findus.missing_persons(id,source_id,name,category,age,age_now,address,photo_url,last_seen_at,created_at,updated_at) VALUES(?,?,'가상아동',?,10,35,'가상장소','https://example.test/photo.jpg',timezone('UTC',now()),timezone('UTC',now()),timezone('UTC',now()))",id,source,category);
        return id;
    }
    private SourcePerson row(String source,String name,String category) { return new SourcePerson(source,name,category,"여자","20000101",10,35,"가상장소","가상옷","가상특징","https://example.test/photo.jpg"); }
    private String login(Member member) throws Exception {
        var result=mvc.perform(post("/api/auth/login").with(this::withCsrf).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",member.getEmail(),"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer "+mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private org.springframework.test.web.servlet.ResultActions register(String token,UUID child) throws Exception { return mvc.perform(put(BASE+"/"+child).header("Authorization",token).with(this::withCsrf)); }
    private org.springframework.test.web.servlet.ResultActions cancel(String token,UUID child) throws Exception { return mvc.perform(delete(BASE+"/"+child).header("Authorization",token).with(this::withCsrf)); }
    private void assertHidden(String token,String status) throws Exception {
        var result=mvc.perform(get(BASE).header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].availability").value(status)).andExpect(jsonPath("$.content[0].child").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("가상아동","가상장소","photo.jpg","sourceId","ageNow");
    }
    private org.springframework.mock.web.MockHttpServletRequest withCsrf(org.springframework.mock.web.MockHttpServletRequest request) {
        try {
            var result=mvc.perform(get("/api/auth/csrf")).andReturn();var csrf=mapper.readTree(result.getResponse().getContentAsString());
            request.addHeader(csrf.get("headerName").asText(),csrf.get("token").asText());request.setCookies(new Cookie[]{result.getResponse().getCookie("XSRF-TOKEN")});return request;
        } catch (Exception e) { throw new IllegalStateException("테스트 CSRF 준비 실패",e); }
    }
    // endregion
}
