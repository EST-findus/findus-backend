package com.findus.backend.aiworkbench.repository;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSave;
import com.findus.backend.aiworkbench.dto.StoredAiImage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 실종자 도메인 엔티티를 건드리지 않도록 이력 테이블과 저장된 사진 조회만 JDBC로 다룹니다. */
@Repository
@Profile("local")
@ConditionalOnProperty(name="app.aiworkbench.enabled",havingValue="true")
public class AiWorkbenchSaveRepositoryImpl implements AiWorkbenchSaveRepository {
    private static final String COLUMNS="id,ai_image_id,missing_person_id,source_image_id,target_age,result_id,image_key,content_sha256,options,saved_at";
    private final JdbcTemplate jdbc;
    public AiWorkbenchSaveRepositoryImpl(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override public Optional<AiWorkbenchSave> findByRequest(String resultId,String imageKey) {
        return jdbc.query("SELECT "+COLUMNS+" FROM findus.ai_workbench_saves WHERE result_id=? AND image_key=?",
                AiWorkbenchSaveRepositoryImpl::row,resultId,imageKey).stream().findFirst();
    }
    @Override public void insert(AiWorkbenchSave s) {
        jdbc.update("INSERT INTO findus.ai_workbench_saves("+COLUMNS+") VALUES (?,?,?,?,?,?,?,?,?,?)",
                s.id(),s.aiImageId(),s.personId(),s.sourceImageId(),s.targetAge(),s.resultId(),s.imageKey(),
                s.contentSha256(),s.options(),s.savedAt());
    }
    @Override public List<AiWorkbenchSave> findByPerson(UUID personId) {
        return jdbc.query("SELECT "+COLUMNS+" FROM findus.ai_workbench_saves WHERE missing_person_id=? ORDER BY saved_at DESC,id",
                AiWorkbenchSaveRepositoryImpl::row,personId);
    }
    @Override public List<AiWorkbenchSave> findByResult(String resultId) {
        return jdbc.query("SELECT "+COLUMNS+" FROM findus.ai_workbench_saves WHERE result_id=? ORDER BY image_key",
                AiWorkbenchSaveRepositoryImpl::row,resultId);
    }
    @Override public Optional<StoredAiImage> findSavedImage(UUID aiImageId) {
        return jdbc.query("SELECT i.content_type,i.content FROM findus.missing_person_ai_images i "
                        +"JOIN findus.ai_workbench_saves s ON s.ai_image_id=i.id WHERE i.id=? AND i.deleted_at IS NULL",
                (rs,n) -> new StoredAiImage(rs.getString(1),rs.getBytes(2)),aiImageId).stream().findFirst();
    }
    private static AiWorkbenchSave row(ResultSet rs,int n) throws SQLException {
        return new AiWorkbenchSave(rs.getObject("id",UUID.class),rs.getObject("ai_image_id",UUID.class),
                rs.getObject("missing_person_id",UUID.class),rs.getObject("source_image_id",UUID.class),
                rs.getInt("target_age"),rs.getString("result_id"),rs.getString("image_key"),
                rs.getString("content_sha256"),rs.getString("options"),
                rs.getObject("saved_at",LocalDateTime.class));
    }
}
