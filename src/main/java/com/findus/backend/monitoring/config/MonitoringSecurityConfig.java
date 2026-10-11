package com.findus.backend.monitoring.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true")
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringSecurityConfig {
	@Bean
	@Order(1)
	public SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http,
			MonitoringProperties properties, PasswordEncoder encoder) throws Exception {
		// 수집 전용 계정은 이 필터 체인에서만 사용하며 회원 인증과 공유하지 않습니다.
		var user = User.withUsername("prometheus").password(encoder.encode(properties.password()))
				.roles("MONITORING").build();
		var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(user));
		provider.setPasswordEncoder(encoder);
		// 회원 인증의 Bearer 응답 대신 수집 계정의 Basic 인증을 명시합니다.
		AuthenticationEntryPoint challenge = (request, response, exception) -> {
			response.setStatus(401);
			response.setHeader("WWW-Authenticate", "Basic realm=\"FindUs monitoring\"");
			response.setContentType("text/plain;charset=UTF-8");
			response.getWriter().write("메트릭 수집 계정 인증이 필요합니다.");
		};
		http.securityMatcher("/actuator/**")
				.authenticationProvider(provider)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("MONITORING")
						.anyRequest().denyAll())
				.httpBasic(basic -> basic.realmName("FindUs monitoring").authenticationEntryPoint(challenge))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(challenge))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable());
		return http.build();
	}
}
