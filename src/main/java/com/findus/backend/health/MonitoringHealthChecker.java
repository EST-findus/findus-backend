package com.findus.backend.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(MonitoringHealthProperties.class)
public class MonitoringHealthChecker {
	private final MonitoringHealthProperties properties;
	private final ObjectMapper mapper;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

	public MonitoringHealthChecker(MonitoringHealthProperties properties, ObjectMapper mapper) {
		this.properties = properties;
		this.mapper = mapper;
	}

	public Map<String, Boolean> check() {
		if (!properties.enabled()) return Map.of();
		var result = new LinkedHashMap<String, Boolean>();
		result.put("Prometheus", available(properties.prometheusUrl().resolve("/-/ready"), false));
		result.put("Grafana", available(properties.grafanaUrl().resolve("/api/health"), true));
		// exporter 포트를 공개하지 않고 Prometheus의 최근 수집 성공 여부를 확인합니다.
		var targets = targets();
		result.put("PostgreSQL exporter", targets.getOrDefault("findus-postgres", false));
		result.put("Redis exporter", targets.getOrDefault("findus-redis", false));
		return result;
	}

	private boolean available(URI uri, boolean grafana) {
		try {
			var response = get(uri);
			return response.statusCode() == 200 && (!grafana
					|| "ok".equals(mapper.readTree(response.body()).path("database").asText()));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException | RuntimeException exception) {
			// 외부 서비스 오류나 주소·내부 설정은 공개 응답에 포함하지 않습니다.
			return false;
		}
	}

	private Map<String, Boolean> targets() {
		try {
			var response = get(properties.prometheusUrl().resolve("/api/v1/targets"));
			if (response.statusCode() != 200) return Map.of();
			var body = mapper.readTree(response.body());
			if (!"success".equals(body.path("status").asText())) return Map.of();
			var result = new LinkedHashMap<String, Boolean>();
			for (var target : body.path("data").path("activeTargets")) {
				String job = target.path("labels").path("job").asText();
				if (job.equals("findus-postgres") || job.equals("findus-redis")) {
					result.merge(job, recentlyCollected(target), Boolean::logicalAnd);
				}
			}
			return result;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return Map.of();
		} catch (IOException | RuntimeException exception) {
			return Map.of();
		}
	}

	private boolean recentlyCollected(JsonNode target) {
		try {
			long age = Duration.between(Instant.parse(target.path("lastScrape").asText()), Instant.now()).toSeconds();
			// 15초 수집 간격을 고려하되 오래된 성공 결과를 정상으로 표시하지 않습니다.
			return "up".equals(target.path("health").asText()) && age >= -5 && age <= 45;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
		return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
