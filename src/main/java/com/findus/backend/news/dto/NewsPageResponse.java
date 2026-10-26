package com.findus.backend.news.dto;

import java.util.List;
public record NewsPageResponse(List<NewsArticleResponse> content,int page,int size,long totalElements,int totalPages) {}
