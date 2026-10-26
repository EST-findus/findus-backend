package com.findus.backend.missing.service;

import java.util.UUID;

/** HTTP API로 공개하지 않는 내부 계약입니다. 향후 관리자 기능·AI 작업에서 호출합니다. */
public interface MissingImageManagementService {
    void ensureOriginalMain(UUID personId);
    void selectOriginalMain(UUID personId,UUID imageId);
    UUID saveAiResult(UUID personId,UUID sourceImageId,int targetAge,byte[] generated);
    void selectAiMain(UUID personId,UUID imageId);
    void deleteAiResult(UUID personId,UUID imageId);
    /** 활성 AI 사진에 스타일 결과를 붙입니다. 같은 스타일이 이미 있으면 DataIntegrityViolationException입니다. */
    UUID saveAiStyle(UUID aiImageId,String sex,String styleKey,byte[] generated,String options);
}
