package com.findus.backend.favorite.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record FavoritePageResponse(List<FavoriteResponse> content,int page,int size,long totalElements,
        int totalPages,boolean hasNext,boolean hasPrevious) {
    public static FavoritePageResponse from(Page<FavoriteResponse> page) {
        return new FavoritePageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements(),
                page.getTotalPages(),page.hasNext(),page.hasPrevious());
    }
}
