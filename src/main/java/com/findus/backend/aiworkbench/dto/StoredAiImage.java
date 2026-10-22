package com.findus.backend.aiworkbench.dto;

/** 작업 화면에서 저장한 AI 결과 사진 바이트. */
public record StoredAiImage(String contentType,byte[] content) {}
