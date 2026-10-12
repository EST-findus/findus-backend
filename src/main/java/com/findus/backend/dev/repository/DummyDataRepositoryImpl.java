package com.findus.backend.dev.repository;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
@ConditionalOnProperty(name = "app.dummy.enabled", havingValue = "true")
public class DummyDataRepositoryImpl implements DummyDataRepository {
    private final JdbcTemplate jdbc;
    public DummyDataRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lock() {
        // 두 서버가 동시에 생성하더라도 중복되지 않도록 트랜잭션 동안만 잠급니다.
        jdbc.execute("SELECT pg_advisory_xact_lock(72810301)");
    }
    @Override
    public boolean isInitialized(String dataSet) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM findus.dummy_data_sets WHERE name = ?)", Boolean.class, dataSet));
    }
    @Override
    public void markInitialized(String dataSet) {
        jdbc.update("INSERT INTO findus.dummy_data_sets(name) VALUES (?) ON CONFLICT(name) DO NOTHING", dataSet);
    }
    @Override
    public void saveMember(UUID id, String email, String passwordHash, String name, String nickname) {
        // UUID가 같은 더미 행만 갱신합니다. 이메일 충돌은 전체 트랜잭션을 롤백합니다.
        jdbc.update("""
                INSERT INTO findus.members(id,email,password_hash,name,nickname,role,status,created_at,updated_at)
                VALUES (?,?,?,?,?,'USER','ACTIVE',timezone('UTC',now()),timezone('UTC',now()))
                ON CONFLICT(id) DO UPDATE SET password_hash=EXCLUDED.password_hash,
                    name=EXCLUDED.name,nickname=EXCLUDED.nickname,role='USER',status='ACTIVE',
                    updated_at=EXCLUDED.updated_at,deleted_at=NULL
                """, id, email, passwordHash, name, nickname);
    }
    @Override
    public void savePost(UUID id, UUID memberId, String title, String content) {
        jdbc.update("""
                INSERT INTO findus.posts(id,member_id,title,content,created_at,updated_at)
                VALUES (?,?,?,?,timezone('UTC',now()),timezone('UTC',now()))
                ON CONFLICT(id) DO UPDATE SET title=EXCLUDED.title,content=EXCLUDED.content,
                    updated_at=EXCLUDED.updated_at,deleted_at=NULL
                """, id, memberId, title, content);
    }
    @Override
    public void saveComment(UUID id, UUID postId, UUID memberId, String content) {
        jdbc.update("""
                INSERT INTO findus.comments(id,post_id,member_id,content,created_at,updated_at)
                VALUES (?,?,?,?,timezone('UTC',now()),timezone('UTC',now()))
                ON CONFLICT(id) DO UPDATE SET content=EXCLUDED.content,
                    updated_at=EXCLUDED.updated_at,deleted_at=NULL
                """, id, postId, memberId, content);
    }
}
