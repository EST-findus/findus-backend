package com.findus.backend.missing.dto;

public record BatchRunResponse(long executionId,String status,SyncCounts counts) {}
