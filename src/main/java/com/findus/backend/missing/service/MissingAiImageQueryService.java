package com.findus.backend.missing.service;

import com.findus.backend.missing.dto.MissingAiImageResponse;
import com.findus.backend.missing.dto.MissingImageContent;
import java.util.Optional;
import java.util.UUID;

public interface MissingAiImageQueryService {
    /** 대표 AI 사진과 저장된 스타일 목록. 대표가 없거나 공개 중이 아니면 비어 있습니다. */
    Optional<MissingAiImageResponse> mainAiImage(UUID personId);
    /** style이 비어 있으면 대표 AI 사진, 있으면 그 스타일 사진입니다. */
    Optional<MissingImageContent> photo(UUID personId,String style);
}
