package com.findus.backend.auth.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepositoryImpl implements AuthSessionRepository {
	// 비교와 교체·삭제를 하나의 명령으로 실행해 동시 재발급과 로그아웃의 충돌을 방지합니다.
	private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
			if redis.call('GET', KEYS[1]) == ARGV[1] then
				redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
				return 1
			end
			return 0
			""", Long.class);
	private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
			if redis.call('GET', KEYS[1]) == ARGV[1] then
				return redis.call('DEL', KEYS[1])
			end
			return 0
			""", Long.class);
	private final StringRedisTemplate redis;

	public AuthSessionRepositoryImpl(StringRedisTemplate redis) { this.redis = redis; }

	@Override
	public void create(UUID sessionId, UUID memberId, String refreshToken, Duration ttl) {
		redis.opsForValue().set(key(sessionId), value(memberId, refreshToken), ttl);
	}

	@Override
	public boolean rotate(UUID sessionId, UUID memberId, String previous, String next, Duration ttl) {
		return Long.valueOf(1).equals(redis.execute(ROTATE, List.of(key(sessionId)),
				value(memberId, previous), value(memberId, next), Long.toString(ttl.getSeconds())));
	}

	@Override
	public boolean revoke(UUID sessionId, UUID memberId, String refreshToken) {
		return Long.valueOf(1).equals(redis.execute(REVOKE, List.of(key(sessionId)), value(memberId, refreshToken)));
	}

	@Override
	public boolean isActive(UUID sessionId, UUID memberId) {
		String value = redis.opsForValue().get(key(sessionId));
		return value != null && value.startsWith(memberId + "|");
	}

	private String key(UUID sessionId) { return "findus:auth:session:" + sessionId; }

	private String value(UUID memberId, String token) {
		// Redis에도 Refresh Token 원문을 저장하지 않습니다.
		try {
			return memberId + "|" + HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("토큰 해시 알고리즘을 사용할 수 없습니다.");
		}
	}
}
