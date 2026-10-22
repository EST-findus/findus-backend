package com.findus.backend.aiworkbench.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="로컬 AI 작업 화면의 저장 결과. created=false는 같은 요청을 이미 저장한 경우입니다.")
public record AiWorkbenchSaveResponse(UUID saveId,UUID aiImageId,UUID personId,UUID sourceImageId,int targetAge,
        String resultId,String imageKey,String contentSha256,String options,LocalDateTime savedAt,boolean created) {
    public static AiWorkbenchSaveResponse from(AiWorkbenchSave s,boolean created) {
        return new AiWorkbenchSaveResponse(s.id(),s.aiImageId(),s.personId(),s.sourceImageId(),s.targetAge(),
                s.resultId(),s.imageKey(),s.contentSha256(),s.options(),s.savedAt(),created);
    }
}
