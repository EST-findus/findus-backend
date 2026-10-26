package com.findus.backend.aiworkbench.dto;

import java.util.UUID;

/** Python 작업 화면이 보낸 저장 요청. options는 생성 옵션·모델 정보 JSON 문자열입니다. */
public record AiWorkbenchSaveCommand(UUID personId,UUID sourceImageId,int targetAge,String resultId,String imageKey,
        String options,byte[] image) {}
