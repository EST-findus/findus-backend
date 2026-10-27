package com.findus.backend.missing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description="아동의 대표 AI 사진과 미리 만들어 둔 머리 스타일. AI가 만든 예상 모습이며 실제 모습이 아닙니다.")
public record MissingAiImageResponse(
        @Schema(description="대표 AI 사진 id") UUID id,
        @Schema(description="생성 시 지정한 목표 나이",example="30") int targetAge,
        LocalDateTime createdAt,
        @Schema(description="기본 AI 사진 주소 (백엔드 기준 상대 경로)") String url,
        @Schema(description="아동 성별에 맞는 스타일 중 저장된 것만, 설정 목록 순서") List<Style> styles,
        @Schema(description="화면에 함께 보여줄 안내 문구") String notice,
        @Schema(description="이 아동의 활성 AI 사진 전체. 대표가 첫 항목이고 나머지는 만든 순서") List<Image> images) {

    public static final String NOTICE="AI가 만든 예상 모습이며 실제 모습이 아닙니다.";

    public MissingAiImageResponse { styles=List.copyOf(styles); images=List.copyOf(images); }

    public record Image(
            @Schema(description="AI 사진 id") UUID id,
            @Schema(description="대표 AI 사진 여부. 대표가 지정되지 않았으면 가장 먼저 만든 사진이 true") boolean main,
            @Schema(description="생성 시 지정한 목표 나이",example="30") int targetAge,
            LocalDateTime createdAt,
            @Schema(description="사진 주소 (백엔드 기준 상대 경로). 대표는 url과 같습니다.") String url) {}

    public record Style(
            @Schema(description="스타일 key",example="bob") String key,
            @Schema(description="버튼에 보여줄 이름",example="단발") String name,
            @Schema(description="스타일 사진 주소 (백엔드 기준 상대 경로)") String url) {}
}
