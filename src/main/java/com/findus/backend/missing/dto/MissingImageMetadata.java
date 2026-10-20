package com.findus.backend.missing.dto;

import java.util.UUID;
/** 이미지 목록에서는 사진 BYTEA를 읽지 않고 메타데이터만 조회합니다. */
public record MissingImageMetadata(UUID id, UUID missingPersonId, int sortOrder, String contentType, int byteSize) {}
