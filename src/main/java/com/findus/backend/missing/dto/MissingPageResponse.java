package com.findus.backend.missing.dto;

import java.util.List;
public record MissingPageResponse(List<MissingPersonResponse> content,int page,int size,long totalElements,int totalPages) {}
