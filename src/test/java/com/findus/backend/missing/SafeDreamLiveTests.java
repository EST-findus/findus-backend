package com.findus.backend.missing;

import com.findus.backend.missing.service.MissingBatchRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** 명시적으로 켰을 때만 실제 API를 호출하며, 저장 대상은 임시 컨테이너입니다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named="SAFE182_LIVE_TEST",matches="true")
class SafeDreamLiveTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm").withUsername("findus").withDatabaseName("findus");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379).withCommand("redis-server","--requirepass","live-test-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));p.add("spring.data.redis.password",() -> "live-test-password");
        p.add("app.missing.esntl-id",() -> System.getenv("SAFE182_ESNTL_ID"));
        p.add("app.missing.auth-key",() -> System.getenv("SAFE182_AUTH_KEY"));
    }
    @Autowired MissingBatchRunner runner;
    @Autowired JdbcTemplate jdbc;
    @Test void 실제_전체_응답을_임시_DB에_저장한다() {
        var result=runner.run();
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.counts().received()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_persons",Integer.class)).isEqualTo(result.counts().received());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findus.missing_persons WHERE source_id IS NULL",Integer.class)).isZero();
        System.out.println("실제 API 검증 완료: 수신·저장 " + result.counts().received() + "건");
    }
}
