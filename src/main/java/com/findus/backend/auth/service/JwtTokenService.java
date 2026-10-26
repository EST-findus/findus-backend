package com.findus.backend.auth.service;

import java.time.Instant;
import java.util.UUID;

import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.exception.AuthException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final AuthProperties properties;

	public JwtTokenService(JwtEncoder encoder, JwtDecoder decoder, AuthProperties properties) {
		this.encoder = encoder;
		this.decoder = decoder;
		this.properties = properties;
	}

	public String issue(UUID memberId, UUID sessionId, String type) {
		Instant now = Instant.now();
		var ttl = "access".equals(type) ? properties.accessTtl() : properties.refreshTtl();
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(memberId.toString())
				.issuedAt(now).notBefore(now).expiresAt(now.plus(ttl)).id(UUID.randomUUID().toString())
				.claim("sid", sessionId.toString()).claim("type", type).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	public Jwt verify(String token, String type) {
		Jwt jwt = decoder.decode(token);
		if (!type.equals(jwt.getClaimAsString("type")) || jwt.getExpiresAt() == null
				|| jwt.getIssuedAt() == null || jwt.getId() == null
				|| jwt.getSubject() == null || jwt.getClaimAsString("sid") == null) {
			throw new AuthException();
		}
		// 회원과 로그인 세션은 이메일 대신 UUID로 식별합니다.
		UUID.fromString(jwt.getSubject());
		UUID.fromString(jwt.getClaimAsString("sid"));
		return jwt;
	}
}
