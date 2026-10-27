package com.findus.backend.missing.controller;

import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.missing.dto.MainPhotoReviewPage;
import com.findus.backend.missing.service.MissingMainPhotoReviewService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 아동별 대표 원본 사진을 사람이 직접 고르는 로컬 작업 화면입니다. 연결된 DB(원격 포함)에 바로 반영합니다. */
@Hidden
@RestController
@Profile("local")
@ConditionalOnProperty(name="app.main-photo-tool.enabled",havingValue="true")
public class MissingMainPhotoToolController {
    private final MissingMainPhotoReviewService service;
    public MissingMainPhotoToolController(MissingMainPhotoReviewService service) { this.service=service; }

    @GetMapping(value="/test/main-photo",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text","html",StandardCharsets.UTF_8))
                .body(new ClassPathResource("local-preview/main-photo-tool.html"));
    }
    @GetMapping("/api/local/main-photo/persons")
    public ResponseEntity<MainPhotoReviewPage> persons(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,
            @RequestParam(defaultValue="true") boolean multiOnly) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.page(page,size,multiOnly));
    }
    @PutMapping("/api/local/main-photo/persons/{personId}/main")
    public ResponseEntity<Void> select(@PathVariable UUID personId,@RequestBody SelectRequest request) {
        if (request.imageId()==null) throw new IllegalArgumentException("imageId를 입력하세요.");
        service.selectMain(personId,request.imageId());
        return ResponseEntity.noContent().build();
    }
    public record SelectRequest(UUID imageId) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> invalid(IllegalArgumentException e) {
        // 서비스 검증 문구에는 입력값이 들어가지 않으므로 그대로 안내합니다.
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST",e.getMessage()));
    }
}
