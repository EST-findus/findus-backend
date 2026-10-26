package com.findus.backend.missing.repository;

import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.entity.MissingPersonAiStyleImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissingPersonAiStyleImageRepository {
    /** 같은 AI 사진·스타일이 이미 있으면 DataIntegrityViolationException을 던집니다. */
    MissingPersonAiStyleImage save(MissingPersonAiStyleImage image);
    List<String> findActiveKeys(UUID aiImageId);
    /** 공개 중인 아동의 현재 대표 AI 사진에 저장된 스타일만 돌려줍니다. */
    Optional<MissingImageContent> findPublicContent(UUID personId,String styleKey);
}
