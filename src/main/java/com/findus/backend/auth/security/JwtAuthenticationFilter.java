package com.findus.backend.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.auth.repository.AuthSessionRepository;
import com.findus.backend.auth.service.JwtTokenService;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 필터는 SecurityFilterChain에만 등록합니다. 서블릿 필터로 중복 등록하지 않습니다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtTokenService tokens;
	private final AuthSessionRepository sessions;
	private final MemberRepository members;
	private final SecurityErrorWriter errors;

	public JwtAuthenticationFilter(JwtTokenService tokens, AuthSessionRepository sessions,
			MemberRepository members, SecurityErrorWriter errors) {
		this.tokens = tokens;
		this.sessions = sessions;
		this.members = members;
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			try {
				var jwt = tokens.verify(header.substring(7), "access");
				UUID id = UUID.fromString(jwt.getSubject());
				UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
				var member = members.findById(id).orElseThrow(AuthException::new);
				if (member.getStatus() != MemberStatus.ACTIVE || !sessions.isActive(sessionId, id)) {
					throw new AuthException();
				}
				// DB의 최신 권한을 사용하므로 비활성화·삭제·권한 변경이 즉시 반영됩니다.
				var principal = new MemberPrincipal(id, sessionId, member.getRole());
				var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
						List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException | AuthException exception) {
				SecurityContextHolder.clearContext();
				errors.write(response, 401, "UNAUTHORIZED", "인증 정보가 올바르지 않거나 만료되었습니다.");
				return;
			} catch (DataAccessException exception) {
				errors.write(response, 503, "AUTH_UNAVAILABLE", "인증 서비스를 잠시 사용할 수 없습니다.");
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
