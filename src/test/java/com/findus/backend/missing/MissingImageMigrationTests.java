package com.findus.backend.missing;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MissingImageMigrationTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11-bookworm")
            .withUsername("findus").withDatabaseName("findus");

    @Test void V9의_기존사진은_첫_활성사진만_대표로_초기화하고_파일을_보존한다() throws Exception {
        migration("9").migrate();
        UUID owner=UUID.randomUUID(),deletedOnly=UUID.randomUUID();
        UUID removed=UUID.randomUUID(),first=UUID.randomUUID(),later=UUID.randomUUID();
        try (var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())) {
            try (var insert=c.prepareStatement("INSERT INTO findus.missing_persons(id,source_id,name,category,last_seen_at,created_at,updated_at) VALUES(?,?, '가상아동','010',now(),now(),now())")) {
                insert.setObject(1,owner);insert.setString(2,"1");insert.executeUpdate();
                insert.setObject(1,deletedOnly);insert.setString(2,"2");insert.executeUpdate();
            }
            String sql="INSERT INTO findus.missing_person_images(id,missing_person_id,sort_order,source_url,content_type,content,byte_size,sha256,created_at,updated_at,deleted_at) VALUES(?,?,?,'출처','image/png',decode('01','hex'),1,repeat('a',64),now(),now(),?)";
            try (var insert=c.prepareStatement(sql)) {
                UUID[] ids={removed,first,later,UUID.randomUUID()};
                for (int i=0;i<ids.length;i++) {
                    insert.setObject(1,ids[i]);insert.setObject(2,i==3?deletedOnly:owner);insert.setInt(3,i==3?0:i);
                    insert.setTimestamp(4,(i==0 || i==3)?java.sql.Timestamp.valueOf("2026-01-01 00:00:00"):null);
                    insert.executeUpdate();
                }
            }
        }
        var flyway=migration("10");flyway.migrate();
        try (var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var q=c.createStatement()) {
            try (var r=q.executeQuery("SELECT id FROM findus.missing_person_images WHERE is_main")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(first);assertThat(r.next()).isFalse();
            }
            try (var r=q.executeQuery("SELECT count(*),sum(byte_size) FROM findus.missing_person_images")) {
                r.next();assertThat(r.getInt(1)).isEqualTo(4);assertThat(r.getInt(2)).isEqualTo(4);
            }
            try (var r=q.executeQuery("SELECT count(*) FROM findus.missing_person_ai_images")) {
                r.next();assertThat(r.getInt(1)).isZero();
            }
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    private Flyway migration(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .defaultSchema("public").locations("classpath:db/migration").target(target).load();
    }
}
