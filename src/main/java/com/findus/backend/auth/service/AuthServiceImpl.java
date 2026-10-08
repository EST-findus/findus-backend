package com.findus.backend.auth.service;

import java.util.UUID;

import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.dto.IssuedTokens;
import com.findus.backend.auth.dto.LoginRequest;
import com.findus.backend.auth.dto.TokenResponse;
import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.auth.repository.AuthSessionRepository;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {
	private final MemberRepository members;
	private final PasswordEncoder passwords;
	private final JwtTokenService tokens;
	private final AuthSessionRepository sessions;
	private final AuthProperties properties;
	private final String dummyHash;

	public AuthServiceImpl(MemberRepository members, PasswordEncoder passwords, JwtTokenService tokens,
			AuthSessionRepository sessions, AuthProperties properties) {
		this.members = members;
		this.passwords = passwords;
		this.tokens = tokens;
		this.sessions = sessions;
		this.properties = properties;
		dummyHash = passwords.encode("non-account-login-placeholder");
	}

	@Override
	public IssuedTokens login(LoginRequest request) {
		Member member = members.findByEmail(request.email()).orElse(null);
		// 없는 계정도 해시 비교를 수행하고, 실패 사유는 동일하게 반환합니다.
		boolean matches;
		try {
			matches = passwords.matches(request.password(), member == null ? dummyHash : member.getPasswordHash());
		} catch (IllegalArgumentException exception) {
			throw new AuthException();
		}
		if (!matches || member == null || member.getStatus() != MemberStatus.ACTIVE) {
			throw new AuthException();
		}
		UUID sessionId = UUID.randomUUID();
		IssuedTokens issued = issue(member.getId(), sessionId);
		sessions.create(sessionId, member.getId(), issued.refreshToken(), properties.refreshTtl());
		return issued;
	}

	@Override
	public IssuedTokens refresh(String refreshToken) {
		var jwt = verifyRefresh(refreshToken);
		UUID memberId = UUID.fromString(jwt.getSubject());
		UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
		Member member = members.findById(memberId).orElseThrow(AuthException::new);
		if (member.getStatus() != MemberStatus.ACTIVE) { throw new AuthException(); }
		IssuedTokens issued = issue(memberId, sessionId);
		if (!sessions.rotate(sessionId, memberId, refreshToken, issued.refreshToken(), properties.refreshTtl())) {
			throw new AuthException();
		}
		return issued;
	}

	@Override
	public void logout(String refreshToken) {
		var jwt = verifyRefresh(refreshToken);
		if (!sessions.revoke(UUID.fromString(jwt.getClaimAsString("sid")),
				UUID.fromString(jwt.getSubject()), refreshToken)) {
			throw new AuthException();
		}
	}

	private org.springframework.security.oauth2.jwt.Jwt verifyRefresh(String token) {
		if (token == null || token.isBlank()) { throw new AuthException(); }
		try {
			return tokens.verify(token, "refresh");
		} catch (JwtException | IllegalArgumentException exception) {
			throw new AuthException();
		}
	}

	private IssuedTokens issue(UUID memberId, UUID sessionId) {
		return new IssuedTokens(new TokenResponse(tokens.issue(memberId, sessionId, "access"),
				"Bearer", properties.accessTtl().getSeconds()), tokens.issue(memberId, sessionId, "refresh"));
	}
}
