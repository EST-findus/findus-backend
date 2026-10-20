package com.findus.backend.missing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.findus.backend.missing.entity.MissingPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MissingPersonRepositoryImpl implements MissingPersonRepository {
    private final MissingPersonJpaRepository jpa;
    private final JdbcTemplate jdbc;
    public MissingPersonRepositoryImpl(MissingPersonJpaRepository jpa,JdbcTemplate jdbc) { this.jpa=jpa;this.jdbc=jdbc; }
    @Override public Optional<MissingPerson> findForFavorite(UUID id) { return jpa.findForFavorite(id); }
    @Override public Optional<MissingPerson> findForImageUpdate(UUID id) { return jpa.findForImageUpdate(id); }
    @Override public List<MissingPerson> findAllForSync() { return jpa.findAll(); }
    @Override public void saveAll(List<MissingPerson> persons) { jpa.saveAllAndFlush(persons); }
    @Override public boolean tryLock() {
        // 실제 동기화 트랜잭션 동안만 잠가 서버 여러 대의 중복 반영을 막습니다.
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(72810302)",Boolean.class));
    }
    @Override public Page<MissingPerson> findAvailable(Pageable page) { return jpa.findAllBySourceAvailableTrueAndDeletedAtIsNull(page); }
    @Override public Optional<MissingPerson> findAvailable(UUID id) { return jpa.findByIdAndSourceAvailableTrueAndDeletedAtIsNull(id); }
}
