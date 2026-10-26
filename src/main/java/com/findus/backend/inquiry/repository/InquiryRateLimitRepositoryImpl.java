package com.findus.backend.inquiry.repository;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** IP별 고정 구간(10분에 5건) 제한입니다. */
@Repository
public class InquiryRateLimitRepositoryImpl implements InquiryRateLimitRepository {
	private static final int LIMIT = 5;
	private static final Duration WINDOW = Duration.ofMinutes(10);
	// 증가와 만료 설정을 하나의 명령으로 실행해 만료 없는 키가 남지 않게 합니다.
	private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""", Long.class);
	private final StringRedisTemplate redis;

	public InquiryRateLimitRepositoryImpl(StringRedisTemplate redis) { this.redis = redis; }

	@Override
	public boolean tryAcquire(String ip) {
		Long count = redis.execute(HIT, List.of("findus:inquiry:rate:" + ip), Long.toString(WINDOW.getSeconds()));
		return count != null && count <= LIMIT;
	}
}
