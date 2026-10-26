package com.findus.backend.auth.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {
	private final SecretKeySpec key;

	public JwtConfig(AuthProperties properties) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(properties.secret());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("JWT_SECRET은 Base64 형식이어야 합니다.");
		}
		if (bytes.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET은 디코딩 후 최소 32바이트여야 합니다.");
		}
		key = new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}

	@Bean
	public JwtDecoder jwtDecoder(AuthProperties properties) {
		var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 발급자·서명·만료를 검사하며 만료 후 유예 시간을 두지 않습니다.
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}
}
