package com.findus.backend.health;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.health.monitoring")
public record MonitoringHealthProperties(boolean enabled, URI prometheusUrl, URI grafanaUrl) {
	public MonitoringHealthProperties {
		if (prometheusUrl == null) prometheusUrl = URI.create("http://localhost:19090");
		if (grafanaUrl == null) grafanaUrl = URI.create("http://localhost:13000");
	}
}
