package com.findus.backend.missing.dto;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="DB에 고정 저장된 사진. URL은 백엔드 기준 상대 경로이며 인증 없이 공개 상태를 확인한 뒤 반환합니다.")
public record MissingImageResponse(UUID id, int sortOrder, String url, String contentType, int byteSize) {
    public static MissingImageResponse from(MissingImageMetadata image) {
        return new MissingImageResponse(image.id(),image.sortOrder(),"/api/missing-persons/"+image.missingPersonId()+"/images/"+image.id(),image.contentType(),image.byteSize());
    }
}
