package com.findus.backend.health;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthServiceImpl implements HealthService {
	private final JdbcTemplate jdbc;
	private final StringRedisTemplate redis;

	public HealthServiceImpl(JdbcTemplate jdbc, StringRedisTemplate redis) {
		this.jdbc = jdbc;
		this.redis = redis;
	}

	@Override
	public HealthStatus check() {
		// 한 항목이 실패해도 다른 항목의 연결을 확인합니다.
		return new HealthStatus(databaseAvailable(), redisAvailable());
	}

	private boolean databaseAvailable() {
		try {
			return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
				try (var statement = connection.createStatement()) {
					// 조회만 수행하고 쿼리가 오래 걸리면 3초 후 중단합니다.
					statement.setQueryTimeout(3);
					try (var result = statement.executeQuery("SELECT 1")) {
						return result.next() && result.getInt(1) == 1;
					}
				}
			}));
		} catch (DataAccessException exception) {
			// 접속 정보와 상세 오류를 공개 응답에 포함하지 않습니다.
			return false;
		}
	}

	private boolean redisAvailable() {
		try {
			return "PONG".equals(redis.execute((RedisCallback<String>) connection -> connection.ping()));
		} catch (DataAccessException exception) {
			return false;
		}
	}
}
