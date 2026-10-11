package com.findus.backend.post.exception;

public class PostForbiddenException extends RuntimeException {
	public PostForbiddenException() { super("작성자만 게시글을 수정하거나 삭제할 수 있습니다."); }
}
