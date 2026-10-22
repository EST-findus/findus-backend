package com.findus.backend.news.controller;

import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.news.dto.NewsApproveRequest;
import com.findus.backend.news.dto.NewsArticleResponse;
import com.findus.backend.news.dto.NewsPageResponse;
import com.findus.backend.news.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/news")
@Tag(name="뉴스 관리",description="관리자 전용. 네이버 검색 결과를 직접 확인하고 승인한 기사만 저장합니다. 쓰기 요청은 CSRF 토큰이 필요합니다.")
public class NewsAdminController {
    private final NewsService service;
    public NewsAdminController(NewsService service) { this.service=service; }

    @GetMapping("/naver")
    @Operation(summary="네이버 뉴스 검색",description="네이버 응답 JSON을 가공 없이 전달하며 저장하지 않습니다. query를 생략하면 설정된 기본 검색어를 씁니다.")
    public JsonNode searchNaverNews(@RequestParam(required=false) String query,@RequestParam(defaultValue="1") int start,
            @RequestParam(required=false) Integer display,@RequestParam(defaultValue="date") String sort) {
        return service.search(query,start,display,sort);
    }

    @PostMapping
    @Operation(summary="기사 승인",description="승인한 기사만 DB에 저장되고 공개 API에 노출됩니다. 같은 원문 주소가 노출 중이면 409입니다.")
    public ResponseEntity<NewsArticleResponse> approveNews(@Parameter(hidden=true) @AuthenticationPrincipal MemberPrincipal admin,
            @Valid @RequestBody NewsApproveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.approve(request,admin.id()));
    }

    @GetMapping
    @Operation(summary="승인한 기사 목록",description="노출 중인 기사를 승인 최신순으로 보여줍니다. page는 0부터, size는 1~100.")
    public NewsPageResponse approvedNews(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.approved(page,size);
    }

    @DeleteMapping("/{id}")
    @Operation(summary="노출 중단",description="공개 목록에서 즉시 제외합니다. 다시 노출하려면 검색해서 재승인합니다.")
    public ResponseEntity<Void> withdrawNews(@PathVariable UUID id) {
        service.withdraw(id);
        return ResponseEntity.noContent().build();
    }
}
