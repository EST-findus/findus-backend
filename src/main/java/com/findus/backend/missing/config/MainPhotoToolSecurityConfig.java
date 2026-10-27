package com.findus.backend.missing.config;

import com.findus.backend.auth.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;
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

/** 대표 사진 작업 화면만 별도 체인으로 분리해 기존 회원 인증 규칙을 바꾸지 않습니다. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@Profile("local")
@ConditionalOnProperty(name="app.main-photo-tool.enabled",havingValue="true")
public class MainPhotoToolSecurityConfig {
    @Bean
    @Order(3)
    public SecurityFilterChain mainPhotoToolSecurityFilterChain(HttpSecurity http,SecurityErrorWriter errors) throws Exception {
        http.securityMatcher("/test/main-photo","/api/local/main-photo/**")
                // 쿠키 인증을 쓰지 않고 아래 필터에서 이 PC·같은 화면의 요청만 통과시키므로 CSRF·CORS는 끕니다.
                .csrf(csrf -> csrf.disable()).cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new SameMachineFilter(errors),AuthorizationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable());
        return http.build();
    }

    /**
     * 이 PC에서 localhost로 연 화면의 요청만 받습니다. Host 확인은 DNS 리바인딩을, 변경 요청의 사용자 정의 헤더와
     * Sec-Fetch-Site 확인은 다른 사이트가 보낸 요청을 막습니다.
     */
    static class SameMachineFilter extends OncePerRequestFilter {
        private static final Set<String> LOCAL_HOSTS=Set.of("localhost","127.0.0.1","[::1]","::1");
        private final SecurityErrorWriter errors;
        SameMachineFilter(SecurityErrorWriter errors) { this.errors=errors; }
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                throws ServletException,IOException {
            if (!isLoopback(request.getRemoteAddr()) || !LOCAL_HOSTS.contains(request.getServerName())) {
                errors.write(response,403,"FORBIDDEN","이 PC에서 localhost로 접속한 경우만 사용할 수 있습니다.");
                return;
            }
            String site=request.getHeader("Sec-Fetch-Site");
            boolean change=!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
            if ((site!=null && !site.equals("same-origin") && !site.equals("none"))
                    || (change && !"main-photo".equals(request.getHeader("X-Findus-Tool")))) {
                errors.write(response,403,"FORBIDDEN","대표 사진 작업 화면에서만 변경할 수 있습니다.");
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
