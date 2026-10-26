package com.findus.backend.aistyle.controller;

import com.findus.backend.aistyle.dto.AiStyleGenerationStatus;
import com.findus.backend.aistyle.service.AiStyleGenerationService;
import com.findus.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-styles/generate")
@Tag(name="AI 스타일 생성",description="관리자 전용. 대표 AI 사진에 아동 성별에 맞는 머리 스타일을 미리 만들어 저장합니다. 나이 변환은 하지 않습니다.")
public class AiStyleAdminController {
    private final AiStyleGenerationService service;
    public AiStyleAdminController(AiStyleGenerationService service) { this.service=service; }

    @PostMapping
    @Operation(summary="머리 스타일 일괄 생성 시작",description="아직 없는 스타일만 한 장씩 순서대로 만듭니다(1장 약 40~80초). 완료를 기다리지 않고 202를 돌려주며 진행 상태는 GET으로 봅니다.")
    @ApiResponses({
        @ApiResponse(responseCode="202",description="시작함",content=@Content(mediaType="application/json",schema=@Schema(implementation=AiStyleGenerationStatus.class))),
        @ApiResponse(responseCode="409",description="이미 실행 중",content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
    })
    public ResponseEntity<?> start(@Parameter(description="한 아동만 만들 때 아동 id. 비우면 대표 AI 사진이 있는 모든 아동") @RequestParam(required=false) UUID personId) {
        return service.start(personId).<ResponseEntity<?>>map(status -> ResponseEntity.status(HttpStatus.ACCEPTED).body(status))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of("AI_STYLE_RUNNING","이미 스타일을 생성하고 있습니다.")));
    }

    @GetMapping
    @Operation(summary="머리 스타일 일괄 생성 진행 상태")
    public AiStyleGenerationStatus status() { return service.status(); }
}
