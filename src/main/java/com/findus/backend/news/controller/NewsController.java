package com.findus.backend.news.controller;

import java.util.List;
import com.findus.backend.news.dto.NewsArticleResponse;
import com.findus.backend.news.entity.NewsPlacement;
import com.findus.backend.news.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@Tag(name="뉴스",description="관리자가 승인한 관련 기사")
public class NewsController {
    private final NewsService service;
    public NewsController(NewsService service) { this.service=service; }
    @GetMapping
    @Operation(summary="공개 기사 목록",description="placement는 HOME 또는 MYPAGE. 해당 위치(또는 BOTH)에 승인됐고 종료일이 지나지 않은 기사를 승인 최신순으로 최대 30건 제공합니다.")
    public List<NewsArticleResponse> publishedNews(@RequestParam(defaultValue="HOME") NewsPlacement placement) { return service.published(placement); }
}
