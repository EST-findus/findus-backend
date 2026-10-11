package com.findus.backend.post.exception;

public class PostPageException extends RuntimeException {
	public PostPageException() { super("페이지 또는 정렬 조건을 확인해 주세요."); }
}
