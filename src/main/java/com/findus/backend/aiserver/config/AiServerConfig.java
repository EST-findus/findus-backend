package com.findus.backend.aiserver.config;

import com.findus.backend.aiserver.client.AiServerClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiServerProperties.class)
public class AiServerConfig {
    @Bean
    public AiServerClient aiServerClient(AiServerProperties properties) {
        return new AiServerClient(properties.baseUrl(), properties.apiKey(), properties.timeout());
    }
}
