package com.findus.backend.auth.config;

import java.util.List;

import com.findus.backend.auth.repository.AuthSessionRepository;
import com.findus.backend.auth.security.JwtAuthenticationFilter;
import com.findus.backend.auth.security.SecurityErrorWriter;
import com.findus.backend.auth.service.JwtTokenService;
import com.findus.backend.member.repository.MemberRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties,
			JwtTokenService tokens, AuthSessionRepository sessions, MemberRepository members,
			SecurityErrorWriter errors) throws Exception {
		var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrf.setCookieCustomizer(cookie -> cookie.secure(properties.cookieSecure()).sameSite("Lax"));
		http.cors(cors -> {})
				.csrf(config -> config.csrfTokenRepository(csrf)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
						// 가입은 쿠키 인증을 사용하지 않는 공개 API입니다.
						.ignoringRequestMatchers(request -> request.getMethod().equals("POST")
								&& request.getRequestURI().equals(request.getContextPath() + "/api/members")))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
				.authorizeHttpRequests(auth -> {
					auth
						.requestMatchers(HttpMethod.POST, "/api/members", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/health").permitAll()
						// 게시글 조회만 공개하며 POST·PUT·DELETE는 아래 인증 규칙을 적용합니다.
						.requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated();
				})
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								errors.write(response, 401, "UNAUTHORIZED", "로그인이 필요합니다."))
						.accessDeniedHandler((request, response, exception) ->
								errors.write(response, 403, "FORBIDDEN", "접근 권한이 없거나 CSRF 토큰이 올바르지 않습니다.")))
				.addFilterBefore(new JwtAuthenticationFilter(tokens, sessions, members, errors),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(properties.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
		config.setAllowCredentials(true);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
