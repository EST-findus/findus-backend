package com.findus.backend.news.exception;

import org.springframework.http.HttpStatus;

/** 상태 코드와 화면에 그대로 보여줄 안내를 담습니다. 인증키·원본 응답은 넣지 않습니다. */
public class NewsException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public NewsException(HttpStatus status,String code,String message) { super(message);this.status=status;this.code=code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
