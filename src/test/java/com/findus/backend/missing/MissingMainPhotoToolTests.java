package com.findus.backend.missing;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","app.monitoring.enabled=false","app.health.monitoring.enabled=false",
        "management.server.port=0","app.dummy.enabled=false","app.main-photo-tool.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"local","test"})
@Testcontainers
@DisplayName("로컬 대표 원본 사진 지정 화면 테스트")
class MissingMainPhotoToolTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","main-photo-test-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "main-photo-test-password");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID multi,single,first,second,third,singleImage;

    @BeforeEach void seed() {
        jdbc.update("DELETE FROM findus.missing_persons");
        multi=person("1");first=image(multi,0,true);second=image(multi,1,false);third=image(multi,2,false);
        single=person("2");singleImage=image(single,0,true);
    }
    UUID person(String sourceId) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO findus.missing_persons(id,source_id,name,category,source_available,last_seen_at,created_at,updated_at) "
                +"VALUES (?,?,?,'010',TRUE,now(),now(),now())",id,sourceId,"가상아동"+sourceId);
        return id;
    }
    UUID image(UUID owner,int order,boolean main) {
        UUID id=UUID.randomUUID();byte[] bytes={1,2,3};
        jdbc.update("INSERT INTO findus.missing_person_images(id,missing_person_id,sort_order,source_url,content_type,content,byte_size,sha256,is_main,created_at,updated_at) "
                +"VALUES (?,?,?,'http://test','image/png',?,?,?,?,now(),now())",id,owner,order,bytes,bytes.length,"0".repeat(64),main);
        return id;
    }
    UUID main(UUID owner) {
        return jdbc.queryForObject("SELECT id FROM findus.missing_person_images WHERE missing_person_id=? AND is_main",UUID.class,owner);
    }
    MockHttpServletRequestBuilder select(UUID owner,UUID image) {
        return put("/api/local/main-photo/persons/"+owner+"/main").header("X-Findus-Tool","main-photo")
                .contentType(MediaType.APPLICATION_JSON).content("{\"imageId\":\""+image+"\"}");
    }

    @Test void 화면과_목록은_사진2장이상_아동과_대표여부를_보여준다() throws Exception {
        mvc.perform(get("/test/main-photo")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/local/main-photo/persons")));
        mvc.perform(get("/api/local/main-photo/persons")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.dbHost").isString())
                .andExpect(jsonPath("$.content[0].id").value(multi.toString()))
                .andExpect(jsonPath("$.content[0].images.length()").value(3))
                .andExpect(jsonPath("$.content[0].images[0].main").value(true))
                .andExpect(jsonPath("$.content[0].images[1].url").value("/api/missing-persons/"+multi+"/images/"+second));
        mvc.perform(get("/api/local/main-photo/persons").param("multiOnly","false")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/local/main-photo/persons").param("size","51")).andExpect(status().isBadRequest());
    }

    @Test void 사진을_고르면_대표가_한장만_바뀌고_공개API에_반영된다() throws Exception {
        mvc.perform(select(multi,third)).andExpect(status().isNoContent());
        assertThat(main(multi)).isEqualTo(third);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_images WHERE missing_person_id=? AND is_main",Integer.class,multi)).isEqualTo(1);
        mvc.perform(select(multi,third)).andExpect(status().isNoContent());
        mvc.perform(get("/api/missing-persons/"+multi)).andExpect(jsonPath("$.photoUrl").value("/api/missing-persons/"+multi+"/images/"+third));
        mvc.perform(select(multi,singleImage)).andExpect(status().isBadRequest());
        assertThat(main(multi)).isEqualTo(third);
        assertThat(main(single)).isEqualTo(singleImage);
    }

    @Test void 다른사이트_다른PC_도구헤더없는_변경요청은_막는다() throws Exception {
        mvc.perform(put("/api/local/main-photo/persons/"+multi+"/main").contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageId\":\""+second+"\"}")).andExpect(status().isForbidden());
        mvc.perform(select(multi,second).header("Sec-Fetch-Site","cross-site")).andExpect(status().isForbidden());
        mvc.perform(select(multi,second).with(r -> { r.setRemoteAddr("10.0.0.5");return r; })).andExpect(status().isForbidden());
        mvc.perform(get("/api/local/main-photo/persons").with(r -> { r.setServerName("attacker.example");return r; })).andExpect(status().isForbidden());
        assertThat(main(multi)).isEqualTo(first);
    }
}
