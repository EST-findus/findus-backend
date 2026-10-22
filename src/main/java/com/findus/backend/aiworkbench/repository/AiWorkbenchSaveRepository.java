package com.findus.backend.aiworkbench.repository;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSave;
import com.findus.backend.aiworkbench.dto.StoredAiImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiWorkbenchSaveRepository {
    Optional<AiWorkbenchSave> findByRequest(String resultId,String imageKey);
    /** 같은 요청 번호가 이미 있으면 DuplicateKeyException을 던집니다. */
    void insert(AiWorkbenchSave save);
    List<AiWorkbenchSave> findByPerson(UUID personId);
    List<AiWorkbenchSave> findByResult(String resultId);
    /** 작업 화면으로 저장한 활성 AI 사진만 돌려줍니다. */
    Optional<StoredAiImage> findSavedImage(UUID aiImageId);
}
