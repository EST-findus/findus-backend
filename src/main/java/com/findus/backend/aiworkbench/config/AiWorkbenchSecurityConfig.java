package com.findus.backend.aiworkbench.config;

import com.findus.backend.auth.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 작업 저장 API만 별도 체인으로 분리해 기존 회원 인증 규칙을 바꾸지 않습니다. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@Profile("local")
@ConditionalOnProperty(name="app.aiworkbench.enabled",havingValue="true")
public class AiWorkbenchSecurityConfig {
    @Bean
    @Order(2)
    public SecurityFilterChain aiWorkbenchSecurityFilterChain(HttpSecurity http,SecurityErrorWriter errors) throws Exception {
        http.securityMatcher("/api/local/aiworkbench/**")
                // 쿠키 인증을 쓰지 않고 브라우저 요청 자체를 아래 필터에서 막으므로 CSRF·CORS는 끕니다.
                .csrf(csrf -> csrf.disable()).cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new LocalServerOnlyFilter(errors),AuthorizationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable());
        return http.build();
    }

    /** 이 PC의 Python 서버가 보낸 요청만 통과시킵니다. 브라우저는 Origin·Sec-Fetch-Site를 붙이므로 다른 사이트의 요청도 막힙니다. */
    static class LocalServerOnlyFilter extends OncePerRequestFilter {
        private final SecurityErrorWriter errors;
        LocalServerOnlyFilter(SecurityErrorWriter errors) { this.errors=errors; }
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                throws ServletException,IOException {
            boolean fromBrowser=request.getHeader("Origin")!=null || request.getHeader("Sec-Fetch-Site")!=null;
            if (!isLoopback(request.getRemoteAddr()) || fromBrowser) {
                errors.write(response,403,"FORBIDDEN","로컬 AI 작업 서버에서만 사용할 수 있습니다.");
                return;
            }
            chain.doFilter(request,response);
        }
        private static boolean isLoopback(String address) {
            try { return address!=null && InetAddress.getByName(address).isLoopbackAddress(); }
            catch (IOException e) { return false; }
        }
    }
}
