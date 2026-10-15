package com.findus.backend.missing.controller;

import java.util.UUID;
import com.findus.backend.missing.dto.MissingPageResponse;
import com.findus.backend.missing.dto.MissingPersonResponse;
import com.findus.backend.missing.service.MissingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name="실종자",description="안전Dream에서 동기화한 공개 정보. 자료 출처: 경찰청")
@RequestMapping("/api/missing-persons")
public class MissingPersonController {
    private final MissingQueryService service;
    public MissingPersonController(MissingQueryService service) { this.service=service; }
    @GetMapping
    @Operation(summary="실종자 목록",description="첫 페이지는 0, 크기는 1~100. 공개 중단 정보는 제외하며 원본 발생일 내림차순으로 정렬합니다.")
    public MissingPageResponse list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return service.list(page,size); }
    @GetMapping("/{id}")
    @Operation(summary="실종자 상세",description="우리 DB의 UUID로 조회합니다. 경찰청 원본 식별자는 sourceId, 발생일은 원본 문자열입니다.")
    public MissingPersonResponse detail(@PathVariable UUID id) { return service.detail(id); }
}
