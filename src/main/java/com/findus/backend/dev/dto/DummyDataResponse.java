package com.findus.backend.dev.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "더미 데이터 생성 결과. 개수는 해당 데이터 세트의 최초 구성 수입니다.")
public record DummyDataResponse(
        @Schema(description = "startup: 시작 시 갱신, persistent: 수동 생성 후 유지") String dataSet,
        @Schema(description = "이번 요청에서 생성 또는 갱신했는지 여부") boolean changed,
        int members, int posts, int comments) {}
