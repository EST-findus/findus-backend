package com.findus.backend.aiworkbench;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveCommand;
import com.findus.backend.aiworkbench.service.AiWorkbenchService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","app.monitoring.enabled=false","app.health.monitoring.enabled=false",
        "management.server.port=0","app.dummy.enabled=false","app.aiworkbench.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"local","test"})
@Testcontainers
@DisplayName("로컬 AI 작업 저장 API 테스트")
class AiWorkbenchSaveTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","workbench-test-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "workbench-test-password");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AiWorkbenchService service;
    UUID person,source,otherPerson,otherSource;

    @BeforeEach void seed() throws Exception {
        jdbc.update("DELETE FROM findus.missing_persons");
        person=person("1");source=image(person);
        otherPerson=person("2");otherSource=image(otherPerson);
    }
    UUID person(String sourceId) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO findus.missing_persons(id,source_id,name,category,source_available,last_seen_at,created_at,updated_at) "
                +"VALUES (?,?,?,'010',TRUE,now(),now(),now())",id,sourceId,"가상아동"+sourceId);
        return id;
    }
    UUID image(UUID owner) throws Exception {
        UUID id=UUID.randomUUID();byte[] bytes=png(1);
        jdbc.update("INSERT INTO findus.missing_person_images(id,missing_person_id,sort_order,source_url,content_type,content,byte_size,sha256,created_at,updated_at) "
                +"VALUES (?,?,0,'http://test','image/png',?,?,?,now(),now())",id,owner,bytes,bytes.length,"0".repeat(64));
        return id;
    }
    static byte[] png(int seed) throws java.io.IOException {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,seed);
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
    }
    MockMultipartHttpServletRequestBuilder save(UUID owner,UUID original,int age,String resultId,byte[] bytes) {
        return multipart("/api/local/aiworkbench/saves").file(new MockMultipartFile("image","age.png","image/png",bytes))
                .param("personId",owner.toString()).param("sourceImageId",original.toString()).param("targetAge",Integer.toString(age))
                .param("resultId",resultId).param("imageKey","age"+age).param("options","{\"strength\":1.0}");
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM findus."+table,Integer.class); }

    // region 저장·재시도·충돌
    @Test void 선택한_결과를_저장하고_재요청은_기존_결과를_돌려준다() throws Exception {
        byte[] bytes=png(30);
        String body=mvc.perform(save(person,source,30,"20261021-120000-a1b2c3",bytes)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true)).andExpect(jsonPath("$.targetAge").value(30))
                .andReturn().getResponse().getContentAsString();
        String aiImageId=com.jayway.jsonpath.JsonPath.read(body,"$.aiImageId");
        mvc.perform(save(person,source,30,"20261021-120000-a1b2c3",bytes)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false)).andExpect(jsonPath("$.aiImageId").value(aiImageId));
        assertThat(count("missing_person_ai_images")).isEqualTo(1);
        assertThat(count("ai_workbench_saves")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT options FROM findus.ai_workbench_saves",String.class)).isEqualTo("{\"strength\":1.0}");
        mvc.perform(get("/api/local/aiworkbench/ai-images/"+aiImageId)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes));
        mvc.perform(get("/api/local/aiworkbench/saves").param("personId",person.toString()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].imageKey").value("age30"));
        mvc.perform(get("/api/local/aiworkbench/saves").param("resultId","20261021-120000-a1b2c3"))
                .andExpect(jsonPath("$.length()").value(1));
        // 없는 사진은 /error 로 넘기지 않고 바로 404 (넘기면 기본 보안 체인에서 401 이 된다)
        mvc.perform(get("/api/local/aiworkbench/ai-images/"+UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(request().attribute("jakarta.servlet.error.status_code",org.hamcrest.Matchers.nullValue()));
    }
    @Test void 같은_요청번호로_다른_사진이나_나이가_오면_충돌로_거절한다() throws Exception {
        mvc.perform(save(person,source,30,"r1",png(30))).andExpect(status().isCreated());
        mvc.perform(save(person,source,30,"r1",png(31))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAVE_CONFLICT"));
        assertThat(count("missing_person_ai_images")).isEqualTo(1);
    }
    @Test void 동시에_같은_요청이_와도_한_장만_저장한다() throws Exception {
        byte[] bytes=png(40);
        var command=new AiWorkbenchSaveCommand(person,source,40,"r-same","age40",null,bytes);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var a=pool.submit(() -> { start.await();return service.save(command); });
            var b=pool.submit(() -> { start.await();return service.save(command); });
            start.countDown();
            var first=a.get(15,TimeUnit.SECONDS);var second=b.get(15,TimeUnit.SECONDS);
            assertThat(first.aiImageId()).isEqualTo(second.aiImageId());
            assertThat(first.created()^second.created()).isTrue();
            assertThat(count("missing_person_ai_images")).isEqualTo(1);
            assertThat(count("ai_workbench_saves")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    // endregion

    // region 검증
    @Test void 범위밖_나이와_다른아동_원본과_잘못된_사진은_저장하지_않는다() throws Exception {
        for (int age:new int[]{0,101}) mvc.perform(save(person,source,age,"r-age"+age,png(1))).andExpect(status().isBadRequest());
        mvc.perform(save(person,otherSource,30,"r-other",png(1))).andExpect(status().isBadRequest());
        mvc.perform(save(person,source,30,"r-html","<html>오류</html>".getBytes())).andExpect(status().isBadRequest());
        mvc.perform(save(person,source,30,"../bad",png(1))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE findus.missing_persons SET source_available=FALSE WHERE id=?",person);
        mvc.perform(save(person,source,30,"r-hidden",png(1))).andExpect(status().isBadRequest());
        assertThat(count("missing_person_ai_images")).isZero();
        assertThat(count("ai_workbench_saves")).isZero();
    }
    // endregion

    // region 접근 제한
    @Test void 브라우저_요청과_다른_PC_요청은_막고_기존_공개API는_그대로다() throws Exception {
        mvc.perform(save(person,source,30,"r-origin",png(1)).header("Origin","http://evil.example")).andExpect(status().isForbidden());
        mvc.perform(save(person,source,30,"r-fetch",png(1)).header("Sec-Fetch-Site","cross-site")).andExpect(status().isForbidden());
        mvc.perform(save(person,source,30,"r-remote",png(1)).with(r -> { r.setRemoteAddr("10.0.0.5");return r; })).andExpect(status().isForbidden());
        mvc.perform(get("/api/local/aiworkbench/saves").param("personId",person.toString()).header("Origin","http://localhost:8000"))
                .andExpect(status().isForbidden());
        assertThat(count("ai_workbench_saves")).isZero();
        mvc.perform(get("/api/missing-persons")).andExpect(status().isOk());
    }
    // endregion
}
