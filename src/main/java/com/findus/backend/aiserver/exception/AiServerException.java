package com.findus.backend.aiserver.exception;

/** AI 서버 호출 실패입니다. 사진이나 AI 서버 키는 메시지에 넣지 않습니다. */
public class AiServerException extends RuntimeException {
    public enum Reason { BAD_INPUT, BUSY, UNAVAILABLE }

    private final Reason reason;

    public AiServerException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiServerException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
