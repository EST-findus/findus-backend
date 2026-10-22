package com.findus.backend.aiworkbench.service;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveCommand;
import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveResponse;
import com.findus.backend.aiworkbench.dto.StoredAiImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiWorkbenchService {
    /** 같은 (resultId, imageKey)는 한 번만 저장하고, 재요청에는 기존 결과를 created=false로 돌려줍니다. */
    AiWorkbenchSaveResponse save(AiWorkbenchSaveCommand command);
    List<AiWorkbenchSaveResponse> history(UUID personId,String resultId);
    Optional<StoredAiImage> savedImage(UUID aiImageId);
}
