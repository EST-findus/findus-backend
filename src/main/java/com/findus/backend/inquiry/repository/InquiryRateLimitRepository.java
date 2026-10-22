package com.findus.backend.inquiry.repository;

public interface InquiryRateLimitRepository {
	/** 이번 요청이 허용 한도 안이면 true를 반환합니다. */
	boolean tryAcquire(String ip);
}
