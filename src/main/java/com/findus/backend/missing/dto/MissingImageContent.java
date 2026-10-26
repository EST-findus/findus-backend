package com.findus.backend.missing.dto;

/** 화면에 그대로 내려줄 사진 바이트와 형식입니다. */
public record MissingImageContent(String contentType, byte[] content) {}
