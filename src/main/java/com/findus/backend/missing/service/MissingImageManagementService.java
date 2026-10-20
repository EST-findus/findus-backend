package com.findus.backend.missing.service;

import java.util.UUID;

/** HTTP API로 공개하지 않는 내부 계약입니다. 향후 관리자 기능·AI 작업에서 호출합니다. */
public interface MissingImageManagementService {
    void ensureOriginalMain(UUID personId);
    void selectOriginalMain(UUID personId,UUID imageId);
    UUID saveAiResult(UUID personId,UUID sourceImageId,int targetAge,byte[] generated);
    void selectAiMain(UUID personId,UUID imageId);
    void deleteAiResult(UUID personId,UUID imageId);
}
