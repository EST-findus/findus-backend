package com.findus.backend.missing.controller;

import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.missing.dto.MissingAiImageResponse;
import com.findus.backend.missing.service.MissingAiImageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name="AI 이미지",description="아동별 대표 AI 사진과 미리 만들어 둔 머리 스타일. AI가 만든 예상 모습이며 실제 모습이 아닙니다.")
@RequestMapping("/api/missing-persons/{personId}/ai-image")
public class MissingAiImageController {
    private final MissingAiImageQueryService service;
    public MissingAiImageController(MissingAiImageQueryService service) { this.service=service; }

    // 예외로 404를 던지면 /error로 넘어가 보안 체인의 401이 될 수 있으므로 두 API 모두 여기서 바로 응답합니다.
    @GetMapping
    @Operation(summary="대표 AI 사진 정보",description="대표 AI 사진 주소와 아동 성별에 맞는 스타일 중 저장된 것만 돌려줍니다. "
            +"화면은 url로 기본 사진을 띄우고, 스타일 버튼을 누르면 그 스타일의 url로 사진만 바꿉니다.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="조회 성공",content=@Content(mediaType="application/json",schema=@Schema(implementation=MissingAiImageResponse.class))),
        @ApiResponse(responseCode="404",description="대표 AI 사진이 없거나 공개 중이 아닌 아동",content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
    })
    public ResponseEntity<?> info(@PathVariable UUID personId) {
        return service.mainAiImage(personId).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of("AI_IMAGE_NOT_FOUND","AI 사진을 찾을 수 없습니다.")));
    }

    @GetMapping(value="/photo",produces={"image/png","image/jpeg"})
    @Operation(summary="대표 AI 사진 / 스타일 사진",description="style이 없으면 대표 AI 사진, 있으면 미리 만들어 둔 그 스타일 사진입니다. 저장되지 않은 스타일은 404입니다.")
    public ResponseEntity<byte[]> photo(@PathVariable UUID personId,
            @Parameter(description="스타일 key (정보 API의 styles[].key)",example="bob") @RequestParam(required=false) String style) {
        // 버튼 전환이 바로 되도록 브라우저에만 잠시 보관합니다. 대표가 바뀌어도 최대 10분 안에 새 사진을 받습니다.
        return service.photo(personId,style)
                .map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                        .header("X-Content-Type-Options","nosniff").contentLength(image.content().length).body(image.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
