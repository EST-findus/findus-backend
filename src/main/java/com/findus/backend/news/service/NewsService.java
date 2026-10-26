package com.findus.backend.news.service;

import java.util.List;
import java.util.UUID;
import com.findus.backend.news.dto.NewsApproveRequest;
import com.findus.backend.news.dto.NewsArticleResponse;
import com.findus.backend.news.dto.NewsPageResponse;
import com.findus.backend.news.entity.NewsPlacement;
import tools.jackson.databind.JsonNode;

public interface NewsService {
    JsonNode search(String query,int start,Integer display,String sort);
    NewsArticleResponse approve(NewsApproveRequest request,UUID adminId);
    NewsPageResponse approved(int page,int size);
    void withdraw(UUID id);
    List<NewsArticleResponse> published(NewsPlacement placement);
}
