package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiStyleImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MissingPersonAiStyleImageJpaRepository extends JpaRepository<MissingPersonAiStyleImage,UUID> {
    @Query("select s.styleKey from MissingPersonAiStyleImage s where s.aiImage.id=:aiImageId and s.deletedAt is null")
    List<String> findActiveKeys(@Param("aiImageId") UUID aiImageId);
    // 대표가 바뀌면 이전 대표의 스타일은 자동으로 보이지 않도록 현재 대표 기준으로만 찾습니다.
    @Query("select new com.findus.backend.missing.dto.MissingImageContent(s.contentType,s.content) from MissingPersonAiStyleImage s "
            +"join s.aiImage a join a.missingPerson p where p.id=:personId and p.sourceAvailable=true and p.deletedAt is null "
            +"and a.main=true and a.deletedAt is null and s.styleKey=:styleKey and s.deletedAt is null")
    Optional<MissingImageContent> findPublicContent(@Param("personId") UUID personId,@Param("styleKey") String styleKey);
}
