package com.findus.backend.auth.controller;

import java.util.Map;
import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.dto.IssuedTokens;
import com.findus.backend.auth.dto.LoginRequest;
import com.findus.backend.auth.dto.TokenResponse;
import com.findus.backend.auth.service.AuthService;
import com.findus.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "인증", description = "로그인·토큰 재발급·로그아웃 및 CSRF 준비")
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

	@Operation(summary = "보안 토큰 준비", description = "로그인·재발급·로그아웃 및 게시글 쓰기 전에 실행하세요. XSRF-TOKEN 쿠키가 준비되며 Swagger가 쓰기 요청에 자동 전달합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "보안 토큰 준비 완료")
	})
	@GetMapping("/csrf")
	public ResponseEntity<Map<String, String>> csrf(@Parameter(hidden = true) CsrfToken token) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
	}

	@Operation(summary = "로그인", description = "먼저 보안 토큰 준비를 실행하세요. accessToken은 응답 본문으로, Refresh Token은 HttpOnly 쿠키로 반환합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "로그인 성공"),
		@ApiResponse(responseCode = "400", description = "입력 형식 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "회원 인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "503", description = "인증 저장소 연결 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return response(service.login(request));
	}

	@Operation(summary = "토큰 재발급", description = "로그인 시 저장된 쿠키를 브라우저가 자동 전송합니다. CSRF가 필요하며 Refresh Token은 재발급할 때 교체됩니다. 새 accessToken으로 Authorize를 갱신하세요.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
		@ApiResponse(responseCode = "401", description = "로그인 쿠키가 없거나 유효하지 않음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "503", description = "인증 저장소 연결 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
		return response(service.refresh(refresh));
	}

	@Operation(summary = "로그아웃", description = "현재 로그인 세션을 차단하고 Refresh 쿠키를 삭제합니다. CSRF가 필요합니다. 이후 Swagger의 Authorize에서도 Logout을 눌러 입력한 토큰을 제거하세요.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "로그아웃 완료", content = @Content),
		@ApiResponse(responseCode = "403", description = "CSRF 검증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "503", description = "인증 저장소 연결 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
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
