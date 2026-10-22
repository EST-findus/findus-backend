package com.findus.backend.aiworkbench.exception;

/** 같은 저장 요청 번호로 다른 사진이나 다른 연결 정보가 들어온 경우입니다. */
public class AiWorkbenchConflictException extends RuntimeException {
    public AiWorkbenchConflictException(String message) { super(message); }
}
