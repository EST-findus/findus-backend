package com.findus.backend.auth.controller;

import java.util.Map;

import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.dto.IssuedTokens;
import com.findus.backend.auth.dto.LoginRequest;
import com.findus.backend.auth.dto.TokenResponse;
import com.findus.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	public static final String REFRESH_COOKIE = "findus_refresh";
	private final AuthService service;
	private final AuthProperties properties;

	public AuthController(AuthService service, AuthProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@GetMapping("/csrf")
	public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return response(service.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
		return response(service.refresh(refresh));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
		service.logout(refresh);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").build();
	}

	private ResponseEntity<TokenResponse> response(IssuedTokens tokens) {
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
						cookie(tokens.refreshToken(), properties.refreshTtl().getSeconds()).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(tokens.response());
	}

	private ResponseCookie cookie(String value, long maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(properties.cookieSecure())
				.sameSite("Lax").path("/api/auth").maxAge(maxAge).build();
	}
}
