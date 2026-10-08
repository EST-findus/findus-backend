package com.findus.backend.health;

public record HealthStatus(boolean database, boolean redis) {
	public boolean healthy() {
		return database && redis;
	}

	public String text() {
		return "Spring Boot: ok\nPostgreSQL: " + (database ? "ok" : "fail")
				+ "\nRedis: " + (redis ? "ok" : "fail");
	}
}
