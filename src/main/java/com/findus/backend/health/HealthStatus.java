package com.findus.backend.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HealthStatus(boolean database, boolean redis, Map<String, Boolean> monitoring) {
	public HealthStatus {
		monitoring = Collections.unmodifiableMap(new LinkedHashMap<>(monitoring));
	}

	public HealthStatus(boolean database, boolean redis) {
		this(database, redis, Map.of());
	}

	public boolean healthy() {
		return database && redis && monitoring.values().stream().allMatch(Boolean.TRUE::equals);
	}

	public String text() {
		var text = new StringBuilder("Spring Boot: ok\nPostgreSQL: " + (database ? "ok" : "fail")
				+ "\nRedis: " + (redis ? "ok" : "fail"));
		monitoring.forEach((name, available) -> text.append("\n").append(name).append(": ")
				.append(available ? "ok" : "fail"));
		return text.toString();
	}
}
