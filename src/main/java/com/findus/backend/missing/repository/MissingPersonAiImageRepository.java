package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingAiImageMetadata;
import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiImage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MissingPersonAiImageRepository {
    MissingPersonAiImage save(MissingPersonAiImage image);
    void saveAll(List<MissingPersonAiImage> images);
    List<MissingPersonAiImage> findForManagement(UUID personId);
    /** 공개 중인 아동의 활성 대표 AI 사진만 돌려줍니다. */
    Optional<MissingAiImageMetadata> findPublicMain(UUID personId);
    Optional<MissingImageContent> findPublicMainContent(UUID personId);
    /** 목록용: 주어진 아동 중 대표 AI 사진이 있는 아동 id. 사진 바이트는 읽지 않습니다. */
    Set<UUID> findPersonIdsWithPublicMain(List<UUID> personIds);
    /** 스타일 일괄 생성 대상: 공개 중인 모든 아동의 대표 AI 사진입니다. */
    List<MissingAiImageMetadata> findPublicMains();
    Optional<MissingPersonAiImage> findActive(UUID id);
}
