package com.findus.backend.missing.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

/** 실제 데이터 없이 화면 코드만 전달하며 데이터·사진은 기존 DB 조회 API에서 읽습니다. */
@Hidden
@RestController
@Profile("local")
public class MissingPreviewController {
    @GetMapping(value="/test/safe182", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(new ClassPathResource("local-preview/safe182-preview.html"));
    }
}
