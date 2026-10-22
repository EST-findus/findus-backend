package com.findus.backend.missing;

import com.findus.backend.missing.service.MissingBatchRunner;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@SpringBootTest(properties={"app.missing.esntl-id=test","app.missing.auth-key=test","app.missing.photo-interval-ms=0"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("실종아동 다중 사진 DB 동기화·조회 통합 테스트")
class MissingImageSyncTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","image-test-password");
    static volatile String mode="normal";
    static volatile boolean changed;
    static final HttpServer SERVER=server();
    static HttpServer server() {
        try {
            var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/api/lcm/findChildList.do",e -> {
                String request=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if (!request.contains("writngTrgetDscds=010")) { e.sendResponseHeaders(400,-1);e.close();return; }
                String rows=row(1)+(mode.equals("withdraw")?"":","+row(2));
                byte[] bytes=("{\"result\":\"00\",\"totalCount\":"+(mode.equals("withdraw")?1:2)+",\"list\":["+rows+"]}").getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
            });
            s.createContext("/home/lcm/lcmMssGet.do",e -> {
                if (mode.equals("detailFail")) { e.sendResponseHeaders(503,-1);e.close();return; }
                String query=e.getRequestURI().getRawQuery();String id=query.substring(query.indexOf("msspsnIdntfccd=")+"msspsnIdntfccd=".length());
                e.getResponseHeaders().add("Set-Cookie","PROFILE_ID="+id+"; Path=/; HttpOnly");
                int count=mode.equals("noPhotos")?0:(id.equals("1")?(mode.equals("fewer")?2:3):2);
                var html=new StringBuilder("<script>function fn_lcmMssImgChange(){}</script>");
                for (int i=0;i<count;i++) html.append("<img src='/home/lcm/blobImgListView.do?tknphotoFileIdx=").append(i).append("&amp;p=").append(id).append("'>");
                byte[] bytes=html.toString().getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
            });
            s.createContext("/home/lcm/blobImgListView.do",e -> {
                String cookie=e.getRequestHeaders().getFirst("Cookie");
                if (cookie==null) { e.sendResponseHeaders(403,-1);e.close();return; }
                String query=e.getRequestURI().getRawQuery();int index=Integer.parseInt(query.substring(query.indexOf('=')+1,query.indexOf('&')));
                if (mode.equals("partial") && index==1) { e.sendResponseHeaders(503,-1);e.close();return; }
                // p값이 아닌 세션으로 아동을 정하는 실제 사진 서버 동작을 재현합니다.
                int person=cookie.contains("PROFILE_ID=2")?2:1;
                byte[] bytes=png(person,index,changed);
                e.getResponseHeaders().set("Content-Type","image/png");
                e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
            });
            s.start();return s;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String row(int id) {
        return "{\"msspsnIdntfccd\":\""+id+"\",\"nm\":\"가상아동"+id+"\",\"writngTrgetDscd\":\"010\",\"occrde\":\"20200101\""+(mode.equals("noPhotos")?"":",\"tknphotoFile\":\"있음\"")+"}";
    }
    static byte[] png(int person,int index,boolean edited) throws java.io.IOException {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,(person*100000)+(index*100)+(edited?1:0));
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "image-test-password");
        p.add("app.missing.base-url",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort());
    }
    @Autowired MissingBatchRunner runner;
    @Autowired com.findus.backend.missing.service.MissingImageManagementService management;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void reset() { mode="normal";changed=false;jdbc.update("DELETE FROM findus.missing_persons"); }
    @AfterAll static void stop() { SERVER.stop(0); }
    UUID person(int source) { return jdbc.queryForObject("SELECT id FROM findus.missing_persons WHERE source_id=?",UUID.class,Integer.toString(source)); }
    UUID image(int source,int order) { return jdbc.queryForObject("SELECT i.id FROM findus.missing_person_images i JOIN findus.missing_persons p ON p.id=i.missing_person_id WHERE p.source_id=? AND i.sort_order=?",UUID.class,Integer.toString(source),order); }
    int visible() { return jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_images WHERE deleted_at IS NULL",Integer.class); }

    // region 원본 바이트·외래키·요청 수·멱등성
    @Test void 세션을_분리해_사진을_저장하고_쿠키없이_동일바이트를_제공한다() throws Exception {
        var result=runner.run();assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.counts().imagesSaved()).isEqualTo(5);assertThat(result.counts().photoFailures()).isZero();
        assertThat(result.counts().requests().api()).isEqualTo(1);
        assertThat(result.counts().requests().detail()).isEqualTo(2);
        assertThat(result.counts().requests().image()).isEqualTo(5);
        assertThat(result.counts().requests().total()).isEqualTo(8);
        UUID id=image(1,0);
        mvc.perform(get("/api/missing-persons/"+person(1)+"/images/"+id)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(png(1,0,false)))
                .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/missing-persons/"+person(2)+"/images/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/missing-persons/"+person(1))).andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.photoUrl").value("/api/missing-persons/"+person(1)+"/images/"+id));
        mvc.perform(get("/api/missing-persons")).andExpect(jsonPath("$.content[0].images").isArray());
        assertThat(runner.run().status()).isEqualTo("COMPLETED");assertThat(visible()).isEqualTo(5);assertThat(image(1,0)).isEqualTo(id);
        changed=true;runner.run();assertThat(image(1,0)).isEqualTo(id);
        mvc.perform(get("/api/missing-persons/"+person(1)+"/images/"+id)).andExpect(content().bytes(png(1,0,true)));
        assertThatThrownBy(() -> jdbc.update("UPDATE findus.missing_person_images SET missing_person_id=? WHERE id=?",UUID.randomUUID(),id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    // endregion

    // region 부분 실패·제거·공개 중단·롤백
    @Test void 사진_부분실패와_상세실패는_기존사진을_유지한다() {
        runner.run();UUID stable=image(1,1);
        mode="partial";var result=runner.run();assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.counts().photoFailures()).isEqualTo(2);assertThat(visible()).isEqualTo(5);
        assertThat(image(1,1)).isEqualTo(stable);
        mode="detailFail";result=runner.run();assertThat(result.counts().photoFailures()).isEqualTo(2);assertThat(visible()).isEqualTo(5);
    }
    @Test void 사진_제거는_소프트삭제하고_재등장시_UUID를_유지한다() throws Exception {
        runner.run();UUID third=image(1,2);
        mode="fewer";runner.run();assertThat(visible()).isEqualTo(4);
        mvc.perform(get("/api/missing-persons/"+person(1)+"/images/"+third)).andExpect(status().isNotFound());
        mode="normal";runner.run();assertThat(visible()).isEqualTo(5);assertThat(image(1,2)).isEqualTo(third);
        mode="noPhotos";runner.run();assertThat(visible()).isZero();
    }
    @Test void 공개중단_아동의_사진은_조회할수없고_복귀하면_같은_UUID로_복구한다() throws Exception {
        runner.run();UUID second=person(2),photo=image(2,0);
        mode="withdraw";runner.run();
        mvc.perform(get("/api/missing-persons/"+second+"/images/"+photo)).andExpect(status().isNotFound());
        mode="normal";runner.run();assertThat(person(2)).isEqualTo(second);assertThat(image(2,0)).isEqualTo(photo);
    }
    @Test void 사진_DB오류는_실종자와_사진을_모두_롤백한다() {
        runner.run();String hash=jdbc.queryForObject("SELECT sha256 FROM findus.missing_person_images WHERE id=?",String.class,image(1,0));
        jdbc.execute("ALTER TABLE findus.missing_person_images ADD CONSTRAINT ck_test_image_reject CHECK(sha256='"+hash+"') NOT VALID");
        try {
            changed=true;assertThat(runner.run().status()).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT sha256 FROM findus.missing_person_images WHERE id=?",String.class,image(1,0))).isEqualTo(hash);
            assertThat(visible()).isEqualTo(5);
        } finally { jdbc.execute("ALTER TABLE findus.missing_person_images DROP CONSTRAINT ck_test_image_reject"); }
    }
    @Test void 대표_원본은_선택값을_유지하고_사라지면_첫_활성사진으로_변경한다() throws Exception {
        runner.run();
        assertThat(mainOriginal(1)).isEqualTo(image(1,0));
        UUID chosen=image(1,2);
        management.selectOriginalMain(person(1),chosen);
        runner.run();
        assertThat(mainOriginal(1)).isEqualTo(chosen);
        mvc.perform(get("/api/missing-persons/"+person(1)))
                .andExpect(jsonPath("$.photoUrl").value("/api/missing-persons/"+person(1)+"/images/"+chosen))
                .andExpect(jsonPath("$.images[0].id").value(image(1,0).toString()));
        mode="partial";runner.run();assertThat(mainOriginal(1)).isEqualTo(chosen);
        mode="fewer";runner.run();assertThat(mainOriginal(1)).isEqualTo(image(1,0));
        mode="noPhotos";runner.run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_images WHERE is_main",Integer.class)).isZero();
    }

    @Test void AI는_원본FK와_생성결과만_저장하고_동기화는_AI와_대표선택을_유지한다() throws Exception {
        runner.run();
        byte[] generated=png(9,1,false);
        UUID first=management.saveAiResult(person(1),image(1,2),25,generated);
        UUID second=management.saveAiResult(person(1),image(1,0),35,png(9,2,false));
        assertThat(mainAi(1)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT source_image_id FROM findus.missing_person_ai_images WHERE id=?",UUID.class,first)).isEqualTo(image(1,2));
        assertThat(jdbc.queryForObject("SELECT content FROM findus.missing_person_ai_images WHERE id=?",byte[].class,first)).isEqualTo(generated);
        assertThat(jdbc.queryForObject("SELECT byte_size FROM findus.missing_person_ai_images WHERE id=?",Integer.class,first)).isEqualTo(generated.length);
        assertThat(jdbc.queryForObject("SELECT target_age FROM findus.missing_person_ai_images WHERE id=?",Integer.class,first)).isEqualTo(25);
        assertThat(visible()).isEqualTo(5);
        management.selectAiMain(person(1),second);
        changed=true;mode="fewer";runner.run();
        assertThat(mainAi(1)).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_ai_images",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT content FROM findus.missing_person_ai_images WHERE id=?",byte[].class,first)).isEqualTo(generated);
        // AI 결과는 원본 목록·사진 조회 API에 섞이지 않습니다.
        mvc.perform(get("/api/missing-persons/"+person(1))).andExpect(jsonPath("$.images.length()").value(2));
        mvc.perform(get("/api/missing-persons/"+person(1)+"/images/"+first)).andExpect(status().isNotFound());
        management.deleteAiResult(person(1),second);assertThat(mainAi(1)).isEqualTo(first);
    }

    @Test void 다른아동_원본과_잘못된_AI결과를_서비스와_DB에서_차단한다() throws Exception {
        runner.run();UUID owner=person(1),source=image(1,0);
        assertThatThrownBy(() -> management.saveAiResult(owner,image(2,0),20,png(8,1,false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> management.selectOriginalMain(owner,image(2,0))).isInstanceOf(IllegalArgumentException.class);
        for (int age:new int[]{-1,201}) assertThatThrownBy(() -> management.saveAiResult(owner,source,age,png(8,1,false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> management.saveAiResult(owner,source,20,"<html>오류</html>".getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> management.saveAiResult(owner,source,20,new byte[10485761])).isInstanceOf(IllegalArgumentException.class);
        UUID ai=management.saveAiResult(owner,source,20,png(8,1,false));
        assertThatThrownBy(() -> jdbc.update("UPDATE findus.missing_person_ai_images SET missing_person_id=? WHERE id=?",person(2),ai))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE findus.missing_person_images SET is_main=true WHERE id=?",image(1,1)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID another=management.saveAiResult(owner,source,30,png(8,2,false));
        assertThatThrownBy(() -> jdbc.update("UPDATE findus.missing_person_ai_images SET is_main=true WHERE id=?",another))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> management.selectAiMain(person(2),ai)).isInstanceOf(IllegalArgumentException.class);
        mode="withdraw";runner.run();
        assertThatThrownBy(() -> management.saveAiResult(person(2),image(2,0),20,png(8,1,false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void 동시_AI저장과_대표변경은_아동별로_직렬화하고_대표를_한장만_유지한다() throws Exception {
        runner.run();UUID owner=person(1),source=image(1,0);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(() -> { start.await();return management.saveAiResult(owner,source,20,png(7,1,false)); });
            var second=pool.submit(() -> { start.await();return management.saveAiResult(owner,source,30,png(7,2,false)); });
            start.countDown();UUID a=first.get(15,java.util.concurrent.TimeUnit.SECONDS),b=second.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_ai_images WHERE is_main",Integer.class)).isEqualTo(1);
            var x=pool.submit(() -> management.selectAiMain(owner,a));
            var y=pool.submit(() -> management.selectAiMain(owner,b));
            x.get(15,java.util.concurrent.TimeUnit.SECONDS);y.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(mainAi(1)).isIn(a,b);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_person_ai_images WHERE is_main",Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void 대표_교체중_DB오류가_나면_이전_대표_해제도_롤백한다() throws Exception {
        runner.run();UUID owner=person(1),source=image(1,0);
        UUID before=management.saveAiResult(owner,source,20,png(6,1,false));
        UUID next=management.saveAiResult(owner,source,30,png(6,2,false));
        jdbc.execute("ALTER TABLE findus.missing_person_ai_images ADD CONSTRAINT ck_test_ai_main_reject CHECK(NOT is_main) NOT VALID");
        try {
            assertThatThrownBy(() -> management.selectAiMain(owner,next)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(mainAi(1)).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE findus.missing_person_ai_images DROP CONSTRAINT ck_test_ai_main_reject"); }
    }
    UUID mainOriginal(int source) {
        return jdbc.queryForObject("SELECT i.id FROM findus.missing_person_images i JOIN findus.missing_persons p ON p.id=i.missing_person_id WHERE p.source_id=? AND i.is_main",UUID.class,Integer.toString(source));
    }
    UUID mainAi(int source) {
        return jdbc.queryForObject("SELECT i.id FROM findus.missing_person_ai_images i JOIN findus.missing_persons p ON p.id=i.missing_person_id WHERE p.source_id=? AND i.is_main",UUID.class,Integer.toString(source));
    }
    // endregion
}
