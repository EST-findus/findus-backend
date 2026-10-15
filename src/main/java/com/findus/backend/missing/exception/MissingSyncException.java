package com.findus.backend.missing.exception;

/** 인증정보나 원본 응답을 포함하지 않는 수집 오류입니다. */
public class MissingSyncException extends RuntimeException {
    public MissingSyncException(String message) { super(message); }
}
