package com.findus.backend.aiworkbench.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** 저장 이력 한 건. (resultId, imageKey)가 저장 요청 번호입니다. */
public record AiWorkbenchSave(UUID id,UUID aiImageId,UUID personId,UUID sourceImageId,int targetAge,
        String resultId,String imageKey,String contentSha256,String options,LocalDateTime savedAt) {}
