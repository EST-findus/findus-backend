package com.findus.backend;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 백엔드가 실제 PostgreSQL·Redis와 통신하는지 확인하는 통합 테스트입니다.
 * Docker가 실행 중이어야 합니다. 개발용 DB 대신 별도의 임시 컨테이너를 사용합니다.
 * 테스트 파일도 Git으로 공유하지만 운영 애플리케이션 JAR에는 포함되지 않습니다.
 */
@DisplayName("FindUs DB·Redis 연결 통합 테스트")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FindUsApplicationTests {

	// region 테스트 전용 DB·Redis 준비
	// 테스트에서만 사용하는 비밀번호입니다. 실제 개발·운영 비밀번호가 아닙니다.
	private static final String REDIS_PASSWORD = "integration-test-password";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus")
			.withDatabaseName("findus");

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379)
			.withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

	// Docker가 배정한 임의 포트를 사용합니다. 로컬 .env나 기존 DB를 변경하지 않습니다.
	@DynamicPropertySource
	static void infrastructureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
	}

	// endregion

	// region 검증에 사용할 Spring 객체
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	Flyway flyway;

	// endregion

	// region PostgreSQL·JPA 읽기와 쓰기
	@Test
	@DisplayName("DB에 저장한 값을 JPA로 읽을 수 있다")
	@Transactional // 테스트가 끝나면 임시 테이블과 입력 내용을 롤백합니다.
	void PostgreSQL에_저장한_값을_JPA로_조회한다() {
		// 준비·실행: 이번 테스트에서만 사용할 임시 테이블을 만들고 숫자 42를 저장합니다.
		jdbcTemplate.execute("CREATE TEMP TABLE connection_check (value INTEGER NOT NULL)");
		jdbcTemplate.update("INSERT INTO connection_check (value) VALUES (?)", 42);
		// 확인: JDBC로 저장한 값을 JPA에서도 동일하게 읽는지 검사합니다.
		assertThat(((Number) entityManager.createNativeQuery("SELECT value FROM connection_check")
				.getSingleResult()).intValue()).isEqualTo(42);
	}

	// endregion

	// region Flyway 변경 이력과 재시작
	@Test
	@DisplayName("앱 스키마를 만들고 같은 SQL을 중복 적용하지 않는다")
	void Flyway가_스키마를_만들고_중복_적용하지_않는다() {
		// 앱 시작 때 Flyway가 findus 스키마와 회원·게시글 테이블까지 만들었는지 확인합니다.
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'findus'", Integer.class))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'findus' AND table_name = 'posts'",
				Integer.class)).isEqualTo(1);
		assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
		// 다시 실행해도 이미 적용한 마이그레이션 수는 0이어야 합니다.
		flyway.validate();
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}

	@Test
	@DisplayName("앱을 다시 시작해도 Flyway 이력은 public에 유지된다")
	void 앱을_다시_시작해도_Flyway_이력_위치가_유지된다() {
		// 동일한 테스트 DB로 앱을 한 번 더 시작합니다. 웹 서버는 띄우지 않습니다.
		// 사용자명과 스키마명이 같을 때 이력 위치가 바뀌는 문제를 방지하는 검사입니다.
		try (var restarted = new SpringApplicationBuilder(FindUsApplication.class)
				.web(WebApplicationType.NONE)
				.run("--spring.profiles.active=test",
						"--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
						"--spring.datasource.username=" + POSTGRES.getUsername(),
						"--spring.datasource.password=" + POSTGRES.getPassword(),
						"--spring.data.redis.host=" + REDIS.getHost(),
						"--spring.data.redis.port=" + REDIS.getMappedPort(6379),
						"--spring.data.redis.password=" + REDIS_PASSWORD)) {
			// V1·V2·V3를 다시 적용하지 않고 기존 public 이력 세 건을 사용하는지 확인합니다.
			assertThat(restarted.getBean(Flyway.class).info().current().getVersion().toString())
					.isEqualTo("3");
			assertThat(restarted.getBean(JdbcTemplate.class).queryForObject(
					"SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class))
					.isEqualTo(3);
		}
	}

	// endregion

	// region Redis 저장·조회와 만료 시간
	@Test
	@DisplayName("Redis에 값을 저장·조회하고 만료 시간을 설정할 수 있다")
	void Redis에_값을_저장하고_조회하며_만료시간을_설정한다() {
		// 테스트끼리 충돌하지 않는 키를 만들고 30초 뒤 만료되도록 저장합니다.
		String key = "integration:connection:" + UUID.randomUUID();
		try {
			redisTemplate.opsForValue().set(key, "connected", Duration.ofSeconds(30));
			assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("connected");
			// 30초를 기다리는 대신 남은 만료 시간(TTL)이 설정되어 있는지 확인합니다.
			assertThat(redisTemplate.getExpire(key)).isBetween(1L, 30L);
		} finally {
			// 검사가 실패하더라도 이 테스트에서 만든 키를 정리합니다.
			redisTemplate.delete(key);
		}
	}

	// endregion

	// region 잘못된 인증 요청 차단
	@Test
	@DisplayName("틀린 DB 비밀번호와 Redis 무인증 요청은 거부한다")
	void 잘못된_DB_비밀번호와_Redis_무인증_요청을_거부한다() throws Exception {
		// PostgreSQL: 틀린 비밀번호로 연결하면 예외가 발생해야 합니다.
		assertThatThrownBy(() -> {
			try (var connection = DriverManager.getConnection(
					POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), "incorrect-password")) {
				connection.isValid(1);
			}
		}).isInstanceOf(SQLException.class);
		// Redis: 비밀번호 없이 PING을 보내면 인증 필요(NOAUTH) 응답이어야 합니다.
		assertThat(REDIS.execInContainer("redis-cli", "ping").getStdout()).contains("NOAUTH");
	}

	// endregion
}
