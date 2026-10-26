package com.findus.backend.aiworkbench.controller;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveCommand;
import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveResponse;
import com.findus.backend.aiworkbench.exception.AiWorkbenchConflictException;
import com.findus.backend.aiworkbench.service.AiWorkbenchService;
import com.findus.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("local")
@ConditionalOnProperty(name="app.aiworkbench.enabled",havingValue="true")
@Tag(name="로컬 AI 작업 저장",description="Python 작업 화면에서 고른 AI 결과만 DB에 저장합니다. 이 PC의 서버 간 요청만 받습니다.")
@RequestMapping("/api/local/aiworkbench")
public class AiWorkbenchController {
    private final AiWorkbenchService service;
    public AiWorkbenchController(AiWorkbenchService service) { this.service=service; }

    @PostMapping(value="/saves",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="선택한 AI 결과 저장",description="새로 저장하면 201, 같은 resultId·imageKey를 이미 저장했으면 기존 결과를 200으로 돌려줍니다. "
            +"같은 요청 번호로 다른 사진이 오면 409입니다.")
    public ResponseEntity<AiWorkbenchSaveResponse> save(@RequestParam UUID personId,@RequestParam UUID sourceImageId,
            @RequestParam int targetAge,@RequestParam String resultId,@RequestParam String imageKey,
            @RequestParam(required=false) String options,@RequestPart("image") MultipartFile image) throws IOException {
        var saved=service.save(new AiWorkbenchSaveCommand(personId,sourceImageId,targetAge,resultId,imageKey,options,image.getBytes()));
        return ResponseEntity.status(saved.created()?HttpStatus.CREATED:HttpStatus.OK).body(saved);
    }
    @GetMapping("/saves")
    @Operation(summary="저장 이력",description="personId 또는 resultId로 조회합니다. 최신 저장이 먼저입니다.")
    public List<AiWorkbenchSaveResponse> history(@RequestParam(required=false) UUID personId,@RequestParam(required=false) String resultId) {
        return service.history(personId,resultId);
    }
    @GetMapping(value="/ai-images/{aiImageId}",produces={"image/jpeg","image/png"})
    @Operation(summary="저장한 AI 사진",description="작업 화면으로 저장한 활성 AI 사진만 돌려줍니다.")
    public ResponseEntity<byte[]> image(@PathVariable UUID aiImageId) {
        // 예외로 404를 던지면 /error로 넘어가 기본 보안 체인의 401이 되므로 여기서 바로 응답합니다.
        return service.savedImage(aiImageId)
                .map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                        .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(image.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> invalid(IllegalArgumentException e) {
        // 서비스 검증 문구에는 입력값이 들어가지 않으므로 그대로 안내합니다.
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST",e.getMessage()));
    }
    @ExceptionHandler(AiWorkbenchConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(AiWorkbenchConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of("SAVE_CONFLICT",e.getMessage()));
    }
}
