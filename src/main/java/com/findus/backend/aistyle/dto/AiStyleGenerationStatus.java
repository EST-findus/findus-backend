package com.findus.backend.aistyle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description="머리 스타일 일괄 생성 진행 상태. total은 이번 실행에서 새로 만들 장수입니다.")
public record AiStyleGenerationStatus(
        @Schema(description="IDLE | RUNNING | DONE | FAILED (AI 서버 연결 실패로 중단)") String state,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int total,
        @Schema(description="새로 저장한 장수") int created,
        @Schema(description="그 사이 이미 저장됐거나 대상이 사라져 건너뛴 장수") int skipped,
        @Schema(description="얼굴을 찾지 못하는 등으로 만들지 못한 장수") int failed,
        @Schema(description="지금 만드는 항목 (아동 id / 스타일 key)") String current,
        @Schema(description="최근 실패 사유 (최대 20개)") List<String> errors) {
    public AiStyleGenerationStatus { errors=List.copyOf(errors); }
}
