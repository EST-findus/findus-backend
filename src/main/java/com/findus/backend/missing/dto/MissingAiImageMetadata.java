package com.findus.backend.missing.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** AI 사진 조회·스타일 생성 대상에서 사진 BYTEA를 읽지 않고 쓰는 정보입니다. sex는 원본 문자열입니다. */
public record MissingAiImageMetadata(UUID id, UUID missingPersonId, String sex, int targetAge, LocalDateTime createdAt, boolean main) {}
