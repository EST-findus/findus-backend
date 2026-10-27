package com.findus.backend.aistyle;

import com.findus.backend.aistyle.service.AiStyleGenerationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","app.monitoring.enabled=false","app.health.monitoring.enabled=false",
        "management.server.port=0","app.dummy.enabled=false","app.ai-style.busy-retry-delay=10ms"})
@AutoConfigureMockMvc
@ActiveProfiles({"local","test"})
@Testcontainers
@DisplayName("대표 AI 사진 조회와 머리 스타일 일괄 생성")
class AiStyleTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","style-test-password");
    static final HttpServer AI=startAi();
    static final List<String> requests=new CopyOnWriteArrayList<>();
    static final AtomicInteger hairStatus=new AtomicInteger(200);

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "style-test-password");
        p.add("app.ai-server.base-url",() -> "http://127.0.0.1:"+AI.getAddress().getPort());
    }
    @AfterAll static void stopAi() { AI.stop(0); }

    /** 요청마다 스타일 key를 기록하고, 결과로 2x2 PNG를 돌려주는 가짜 AI 서버입니다. */
    static HttpServer startAi() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/hair",exchange -> {
                String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.ISO_8859_1);
                // 파트 머리에 Content-Type 등이 더 붙을 수 있으므로 빈 줄 다음 값을 읽습니다.
                var matcher=java.util.regex.Pattern.compile("name=\"style\"\r\n(?:[^\r\n]+\r\n)*\r\n([a-z_]+)\r\n").matcher(form);
                String style=matcher.find()?matcher.group(1):"?";
                requests.add(style);
                if (hairStatus.get()!=200) { send(exchange,hairStatus.get(),"application/json","{\"detail\":\"얼굴을 찾지 못했습니다\"}".getBytes(StandardCharsets.UTF_8)); return; }
                send(exchange,200,"application/json",("{\"result_id\":\"r-"+style+"\",\"seconds\":1.0,\"images\":[{\"key\":\"face\",\"url\":\"/v1/results/r/images/face.png\",\"info\":{\"ref\":\"a.jpg\"}}]}").getBytes(StandardCharsets.UTF_8));
            });
            server.createContext("/v1/results/",exchange -> send(exchange,200,"image/png",png(7)));
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    static void send(HttpExchange exchange,int status,String type,byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(status,body.length);
        exchange.getResponseBody().write(body);exchange.close();
    }
    static byte[] png(int seed) {
        try {
            var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
            image.setRGB(0,0,seed);
            var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AiStyleGenerationService generation;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM findus.missing_persons");
        requests.clear();hairStatus.set(200);
    }

    // region 데이터 준비
    UUID person(String sourceId,String sex) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO findus.missing_persons(id,source_id,name,category,sex,source_available,last_seen_at,created_at,updated_at) "
                +"VALUES (?,?,?,'010',?,TRUE,now(),now(),now())",id,sourceId,"가상아동"+sourceId,sex);
        return id;
    }
    UUID aiImage(UUID owner,boolean main) { return aiImage(owner,main,png(1)); }
    UUID aiImage(UUID owner,boolean main,byte[] bytes) {
        UUID source=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO findus.missing_person_images(id,missing_person_id,sort_order,source_url,content_type,content,byte_size,sha256,created_at,updated_at) "
                +"VALUES (?,?,(SELECT count(*) FROM findus.missing_person_images WHERE missing_person_id=?),'http://test','image/png',?,?,?,now(),now())",
                source,owner,owner,bytes,bytes.length,"0".repeat(64));
        jdbc.update("INSERT INTO findus.missing_person_ai_images(id,missing_person_id,source_image_id,target_age,content,content_type,byte_size,is_main,created_at,updated_at) "
                +"VALUES (?,?,?,30,?,'image/png',?,?,now(),now())",id,owner,source,bytes,bytes.length,main);
        return id;
    }
    void style(UUID owner,UUID aiImageId,String sex,String key,byte[] bytes) {
        jdbc.update("INSERT INTO findus.missing_person_ai_style_images(id,ai_image_id,missing_person_id,sex,style_key,content,content_type,byte_size,created_at,updated_at) "
                +"VALUES (?,?,?,?,?,?,'image/png',?,now(),now())",UUID.randomUUID(),aiImageId,owner,sex,key,bytes,bytes.length);
    }
    int styles() { return jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_ai_style_images",Integer.class); }
    void awaitGeneration() throws InterruptedException {
        for (int i=0;i<200 && generation.status().state().equals("RUNNING");i++) Thread.sleep(50);
        assertThat(generation.status().state()).isNotEqualTo("RUNNING");
    }
    // endregion

    // region DB 제약
    @Test void 같은_AI사진의_같은_스타일과_다른_아동_연결은_DB가_거절한다() {
        UUID girl=person("1","여자"),other=person("2","여자");
        UUID ai=aiImage(girl,true);
        style(girl,ai,"F","bob",png(2));
        assertThatThrownBy(() -> style(girl,ai,"F","bob",png(3))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> style(other,ai,"F","long",png(3))).isInstanceOf(DataIntegrityViolationException.class);
    }
    // endregion

    // region 조회 API
    @Test void 대표_AI사진과_성별에_맞는_저장된_스타일만_비회원에게_보여준다() throws Exception {
        UUID girl=person("1","여자");
        UUID old=aiImage(girl,false),main=aiImage(girl,true);
        byte[] bob=png(20),longHair=png(21);
        style(girl,main,"F","long",longHair);style(girl,main,"F","bob",bob);
        style(girl,main,"M","buzz",png(22));    // 남성 목록의 key라 여아에게는 보이지 않습니다.
        style(girl,old,"F","short",png(23));    // 대표가 아닌 AI 사진의 스타일은 보이지 않습니다.
        String base="/api/missing-persons/"+girl+"/ai-image";

        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(main.toString())).andExpect(jsonPath("$.targetAge").value(30))
                .andExpect(jsonPath("$.url").value(base+"/photo"))
                .andExpect(jsonPath("$.styles.length()").value(2))
                // 저장 순서가 아니라 설정 목록 순서(단발 → 긴 머리)입니다.
                .andExpect(jsonPath("$.styles[0].key").value("bob")).andExpect(jsonPath("$.styles[0].name").value("단발"))
                .andExpect(jsonPath("$.styles[0].url").value(base+"/photo?style=bob"))
                .andExpect(jsonPath("$.styles[1].key").value("long"))
                .andExpect(jsonPath("$.notice").isNotEmpty());
        mvc.perform(get(base+"/photo")).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().bytes(png(1)));
        mvc.perform(get(base+"/photo").param("style","bob")).andExpect(status().isOk()).andExpect(content().bytes(bob));
        mvc.perform(get(base+"/photo").param("style","short")).andExpect(status().isNotFound());
    }

    @Test void 아동_정보에_대표_AI사진_주소를_함께_준다() throws Exception {
        UUID withAi=person("1","여자"),noMain=person("2","남자"),without=person("3","남자");
        aiImage(withAi,true);aiImage(noMain,false);   // 대표가 지정되지 않아도 AI 사진이 있으면 보여줍니다.
        mvc.perform(get("/api/missing-persons/"+withAi)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPhotoUrl").value("/api/missing-persons/"+withAi+"/ai-image/photo"));
        mvc.perform(get("/api/missing-persons/"+noMain)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPhotoUrl").value("/api/missing-persons/"+noMain+"/ai-image/photo"));
        mvc.perform(get("/api/missing-persons/"+without)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPhotoUrl").doesNotExist());
    }

    @Test void 대표_AI사진이_없거나_공개중단된_아동은_404다() throws Exception {
        UUID none=person("1","남자"),hidden=person("2","남자");
        aiImage(hidden,true);
        jdbc.update("UPDATE findus.missing_persons SET source_available=FALSE WHERE id=?",hidden);
        for (UUID id:List.of(none,hidden,UUID.randomUUID())) {
            mvc.perform(get("/api/missing-persons/"+id+"/ai-image")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI_IMAGE_NOT_FOUND"));
            mvc.perform(get("/api/missing-persons/"+id+"/ai-image/photo")).andExpect(status().isNotFound());
        }
    }

    @Test void 아동의_활성_AI사진을_대표부터_모두_보여주고_다른_아동_사진은_404다() throws Exception {
        UUID girl=person("1","여자"),other=person("2","여자");
        byte[] first=png(30),second=png(31);
        UUID a=aiImage(girl,false,first),main=aiImage(girl,true),b=aiImage(girl,false,second),deleted=aiImage(girl,false);
        jdbc.update("UPDATE findus.missing_person_ai_images SET deleted_at=now() WHERE id=?",deleted);
        UUID foreign=aiImage(other,true);
        String base="/api/missing-persons/"+girl+"/ai-image";

        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].id").value(main.toString())).andExpect(jsonPath("$.images[0].main").value(true))
                .andExpect(jsonPath("$.images[0].url").value(base+"/photo"))
                .andExpect(jsonPath("$.images[1].id").value(a.toString())).andExpect(jsonPath("$.images[1].main").value(false))
                .andExpect(jsonPath("$.images[1].url").value(base+"/photos/"+a))
                .andExpect(jsonPath("$.images[2].id").value(b.toString()));
        mvc.perform(get(base+"/photos/"+a)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().bytes(first));
        mvc.perform(get(base+"/photos/"+b)).andExpect(status().isOk()).andExpect(content().bytes(second));
        for (UUID id:List.of(deleted,foreign,UUID.randomUUID()))
            mvc.perform(get(base+"/photos/"+id)).andExpect(status().isNotFound());
        jdbc.update("UPDATE findus.missing_persons SET source_available=FALSE WHERE id=?",girl);
        mvc.perform(get(base+"/photos/"+a)).andExpect(status().isNotFound());
    }

    @Test void 대표_AI사진이_없어도_활성_AI사진을_모두_보여주고_먼저_만든_사진을_대표_자리에_쓴다() throws Exception {
        UUID boy=person("1","남자");
        byte[] first=png(40),second=png(41);
        UUID a=aiImage(boy,false,first),b=aiImage(boy,false,second);
        style(boy,a,"M","buzz",png(42));    // 스타일 조회는 is_main 기준이라 대표가 없으면 보이지 않습니다.
        String base="/api/missing-persons/"+boy+"/ai-image";

        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.toString())).andExpect(jsonPath("$.url").value(base+"/photo"))
                .andExpect(jsonPath("$.styles.length()").value(0))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].id").value(a.toString())).andExpect(jsonPath("$.images[0].main").value(true))
                .andExpect(jsonPath("$.images[0].url").value(base+"/photo"))
                .andExpect(jsonPath("$.images[1].id").value(b.toString())).andExpect(jsonPath("$.images[1].main").value(false))
                .andExpect(jsonPath("$.images[1].url").value(base+"/photos/"+b));
        mvc.perform(get(base+"/photo")).andExpect(status().isOk()).andExpect(content().bytes(first));
        mvc.perform(get(base+"/photos/"+b)).andExpect(status().isOk()).andExpect(content().bytes(second));
    }
    // endregion

    // region 일괄 생성
    @Test void 성별별_기본_스타일을_만들고_다시_실행하면_남은_것만_만든다() throws Exception {
        UUID girl=person("1","여자"),boy=person("2","남자"),unknown=person("3",null);
        UUID girlAi=aiImage(girl,true);aiImage(boy,true);aiImage(unknown,true);
        style(girl,girlAi,"F","bob",png(30));   // 이미 있는 스타일은 다시 만들지 않습니다.

        assertThat(generation.start(null)).isPresent();
        awaitGeneration();
        var status=generation.status();
        assertThat(status.state()).as(status.errors().toString()).isEqualTo("DONE");
        assertThat(status.total()).isEqualTo(4+3);
        assertThat(status.created()).isEqualTo(7);
        assertThat(requests).containsExactlyInAnyOrder("short","bob_bang","long","long_bang","buzz","short","short_bang");
        assertThat(styles()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT options FROM findus.missing_person_ai_style_images WHERE style_key='long'",String.class)).contains("a.jpg");
        mvc.perform(get("/api/missing-persons/"+boy+"/ai-image")).andExpect(jsonPath("$.styles.length()").value(3));

        requests.clear();
        generation.start(null);awaitGeneration();
        assertThat(generation.status().total()).isZero();
        assertThat(requests).isEmpty();
    }

    @Test void 얼굴을_못찾으면_그_사진의_남은_스타일은_요청하지_않는다() throws Exception {
        UUID boy=person("1","남자");aiImage(boy,true);
        hairStatus.set(400);
        generation.start(boy);awaitGeneration();
        var status=generation.status();
        assertThat(status.state()).isEqualTo("DONE");
        assertThat(status.failed()).isEqualTo(3);
        assertThat(status.errors()).anyMatch(error -> error.contains("얼굴을 찾지 못했습니다"));
        assertThat(requests).hasSize(1);
        assertThat(styles()).isZero();
    }

    @Test void AI서버가_계속_바쁘면_재시도_후_실패로_남긴다() throws Exception {
        UUID boy=person("1","남자");aiImage(boy,true);
        hairStatus.set(503);
        generation.start(boy);awaitGeneration();
        assertThat(generation.status().failed()).isEqualTo(3);
        // 기본 재시도 3번 + 처음 1번 = 스타일마다 4번
        assertThat(requests).hasSize(12);
    }

    @Test void 일괄_생성_API는_관리자만_쓴다() throws Exception {
        mvc.perform(post("/api/admin/ai-styles/generate")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/ai-styles/generate")).andExpect(status().isUnauthorized());
    }
    // endregion
}
