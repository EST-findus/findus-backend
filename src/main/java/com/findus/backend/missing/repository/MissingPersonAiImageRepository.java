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
    /** 공개 화면의 기본 AI 사진: 대표, 대표가 없으면 가장 먼저 만든 활성 AI 사진입니다. */
    Optional<MissingImageContent> findPublicFirstContent(UUID personId);
    /** 공개 중인 아동의 활성 AI 사진 전체. 대표가 먼저, 나머지는 만든 순서입니다. 사진 바이트는 읽지 않습니다. */
    List<MissingAiImageMetadata> findPublicAll(UUID personId);
    /** 그 아동의 활성 AI 사진일 때만 돌려줍니다. 다른 아동의 사진 id는 비어 있습니다. */
    Optional<MissingImageContent> findPublicContent(UUID personId,UUID aiImageId);
    /** 목록용: 주어진 아동 중 활성 AI 사진이 한 장이라도 있는 아동 id. 사진 바이트는 읽지 않습니다. */
    Set<UUID> findPersonIdsWithPublicAi(List<UUID> personIds);
    /** 스타일 일괄 생성 대상: 공개 중인 모든 아동의 대표 AI 사진입니다. */
    List<MissingAiImageMetadata> findPublicMains();
    Optional<MissingPersonAiImage> findActive(UUID id);
}
