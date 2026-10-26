package com.findus.backend.auth.repository;

import java.time.Duration;
import java.util.UUID;

public interface AuthSessionRepository {
	void create(UUID sessionId, UUID memberId, String refreshToken, Duration ttl);
	boolean rotate(UUID sessionId, UUID memberId, String previous, String next, Duration ttl);
	boolean revoke(UUID sessionId, UUID memberId, String refreshToken);
	boolean isActive(UUID sessionId, UUID memberId);
}
