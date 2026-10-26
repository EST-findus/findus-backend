package com.findus.backend.member;

import java.time.LocalDateTime;
import java.util.UUID;

import com.findus.backend.member.entity.Member;
import com.findus.backend.member.entity.MemberRole;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 PostgreSQL에서 회원 저장 정책을 검증합니다. 개발 DB와 .env는 사용하지 않습니다. */
@DisplayName("UUID 회원 저장·조회·소프트 삭제 통합 테스트")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional // 각 테스트가 저장한 회원은 종료 시 롤백합니다.
class MemberRepositoryTests {

	// region 테스트 전용 환경 준비
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus")
			.withDatabaseName("findus");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		// 이번 테스트는 Redis를 호출하지 않습니다. 공통 설정의 필수 값만 채웁니다.
		registry.add("spring.data.redis.password", () -> "unused-member-test-password");
	}

	// 비밀번호 해시 형태의 테스트용 값이며 실제 계정의 비밀번호가 아닙니다.
	private static final String PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;
	// endregion

	// region UUID·기본값·시간 저장
	@Test
	@DisplayName("UUID와 공통 시간이 생성되고 기본 권한·상태가 저장된다")
	void 회원을_저장하면_UUID와_기본값이_생성된다() {
		Member saved = saveAndReload(new Member(" MEMBER@Example.com ", PASSWORD_HASH, "홍길동", "찾음이"));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getId().version()).isEqualTo(4);
		assertThat(saved.getEmail()).isEqualTo("member@example.com");
		assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
		assertThat(saved.getName()).isEqualTo("홍길동");
		assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
		assertThat(saved.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(saved.getDeletedAt()).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT pg_typeof(id)::text FROM findus.members WHERE id = ?", String.class, saved.getId()))
				.isEqualTo("uuid");
	}

	@Test
	@DisplayName("회원 수정 시 생성 시간은 유지하고 수정 시간을 갱신한다")
	void 회원을_수정하면_수정시간만_갱신된다() {
		Member saved = saveAndReload(member("audit@example.com"));
		UUID id = saved.getId();
		LocalDateTime createdAt = saved.getCreatedAt();
		// 시간을 기다리지 않고, DB에 과거 수정 시간을 넣어 Auditing의 갱신 여부를 확인합니다.
		jdbcTemplate.update("UPDATE findus.members SET updated_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", id);
		entityManager.clear();
		Member loaded = memberRepository.findById(id).orElseThrow();
		loaded.updateMember("변경한닉네임", null);
		memberRepository.save(loaded);
		entityManager.flush();
		entityManager.clear();

		Member updated = memberRepository.findById(id).orElseThrow();
		assertThat(updated.getNickname()).isEqualTo("변경한닉네임");
		assertThat(updated.getPasswordHash()).isEqualTo(PASSWORD_HASH);
		assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
		assertThat(updated.getUpdatedAt()).isAfter(LocalDateTime.of(2000, 1, 1, 0, 0));
	}
	// endregion

	// region 이메일 조회와 DB 중복 방지
	@Test
	@DisplayName("이메일 조회·중복 확인에도 공백 제거와 소문자 정규화가 적용된다")
	void 정규화된_이메일로_회원과_중복여부를_조회한다() {
		Member saved = saveAndReload(member("lookup@example.com"));
		assertThat(memberRepository.findByEmail(" LOOKUP@Example.com ").orElseThrow().getId())
				.isEqualTo(saved.getId());
		assertThat(memberRepository.existsByEmail(" LOOKUP@Example.com ")).isTrue();
		assertThat(memberRepository.findByEmail("missing@example.com")).isEmpty();
		assertThat(memberRepository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("대소문자가 다른 같은 이메일도 DB에서 중복 저장을 거부한다")
	void 같은_이메일의_중복_저장을_거부한다() {
		saveAndReload(member("duplicate@example.com"));
		assertThatThrownBy(() -> {
			memberRepository.save(member(" DUPLICATE@Example.com "));
			entityManager.flush();
		}).isInstanceOf(RuntimeException.class).hasStackTraceContaining("uk_members_email");
	}
	// endregion

	// region 소프트 삭제와 비활성 상태 구분
	@Test
	@DisplayName("소프트 삭제는 행을 유지하고 조회에서 제외하며 이메일 재사용을 막는다")
	void 삭제한_회원은_조회되지_않지만_DB에는_남는다() {
		Member saved = saveAndReload(member("deleted@example.com"));
		UUID id = saved.getId();
		saved.softDelete();
		LocalDateTime firstDeletedAt = saved.getDeletedAt();
		saved.softDelete();
		assertThat(saved.getDeletedAt()).isEqualTo(firstDeletedAt);
		memberRepository.save(saved);
		entityManager.flush();
		entityManager.clear();

		assertThat(memberRepository.findById(id)).isEmpty();
		assertThat(memberRepository.findByEmail("deleted@example.com")).isEmpty();
		assertThat(memberRepository.existsByEmail("deleted@example.com")).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM findus.members WHERE id = ?", Integer.class, id))
				.isEqualTo(1);
		Member deleted = entityManager.find(Member.class, id);
		assertThat(deleted.isDeleted()).isTrue();
		assertThat(deleted.getStatus()).isEqualTo(MemberStatus.INACTIVE);
		assertThatThrownBy(deleted::activate).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> deleted.updateMember("복구시도", null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("비활성 회원은 삭제된 회원과 다르며 다시 활성화할 수 있다")
	void 비활성_회원은_삭제되지_않고_다시_활성화할_수_있다() {
		Member saved = saveAndReload(member("inactive@example.com"));
		saved.deactivate();
		entityManager.flush();
		entityManager.clear();
		Member inactive = memberRepository.findById(saved.getId()).orElseThrow();
		assertThat(inactive.getStatus()).isEqualTo(MemberStatus.INACTIVE);
		assertThat(inactive.isDeleted()).isFalse();
		inactive.activate();
		entityManager.flush();
		entityManager.clear();
		assertThat(memberRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
	}
	// endregion

	// region 입력 검증과 DB 제약조건
	@Test
	@DisplayName("잘못된 변경 요청은 기존 닉네임·비밀번호를 일부만 수정하지 않는다")
	void 잘못된_수정요청은_기존값을_유지한다() {
		Member member = member("validation@example.com");
		assertThatThrownBy(() -> member.updateMember("새닉네임", " ")).isInstanceOf(IllegalArgumentException.class);
		assertThat(member.getNickname()).isEqualTo("찾음이");
		assertThat(member.getPasswordHash()).isEqualTo(PASSWORD_HASH);
		assertThatThrownBy(() -> member(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Member("long@example.com", PASSWORD_HASH, "이름", "가".repeat(51)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("JPA를 거치지 않는 SQL도 정의되지 않은 회원 상태를 저장할 수 없다")
	void DB가_정의되지_않은_상태값을_거부한다() {
		Member saved = saveAndReload(member("constraint@example.com"));
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE findus.members SET status = 'UNKNOWN' WHERE id = ?", saved.getId()))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_members_status");
	}
	// endregion

	// region 테스트 보조 메서드
	private Member member(String email) {
		return new Member(email, PASSWORD_HASH, "홍길동", "찾음이");
	}

	private Member saveAndReload(Member member) {
		Member saved = memberRepository.save(member);
		entityManager.flush();
		UUID id = saved.getId();
		entityManager.clear();
		return memberRepository.findById(id).orElseThrow();
	}
	// endregion
}
