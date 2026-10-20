package com.findus.backend.missing;

import com.findus.backend.auth.config.AuthProperties;
import com.findus.backend.auth.config.SecurityConfig;
import com.findus.backend.auth.repository.AuthSessionRepository;
import com.findus.backend.auth.security.SecurityErrorWriter;
import com.findus.backend.auth.service.JwtTokenService;
import com.findus.backend.member.repository.MemberRepository;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MissingSyncSecurityTests {
    @Test void local에서만_비회원과_CSRF없는_실행을_허용한다() throws Exception {
        for (String profile:List.of("local","prod","test")) {
            try (var context=new AnnotationConfigWebApplicationContext()) {
                context.setServletContext(new MockServletContext());
                context.getEnvironment().setActiveProfiles(profile);
                context.register(Fixture.class,SecurityConfig.class);
                context.refresh();
                var mvc=MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
                boolean local=profile.equals("local");
                mvc.perform(post("/api/admin/missing-sync")).andExpect(status().is(local?200:403));
                mvc.perform(get("/api/admin/missing-sync")).andExpect(status().is(local?200:401));
                mvc.perform(get("/api/admin/other-feature")).andExpect(status().isUnauthorized());
                mvc.perform(post("/api/auth/login")).andExpect(status().isForbidden());
            }
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    @EnableWebSecurity
    @EnableWebMvc
    static class Fixture {
        @Bean AuthProperties properties() {
            return new AuthProperties("test-secret", "test", Duration.ofMinutes(5),Duration.ofDays(1),false,List.of("http://localhost:5173"));
        }
        @Bean JwtTokenService tokens() { return mock(JwtTokenService.class); }
        @Bean AuthSessionRepository sessions() { return mock(AuthSessionRepository.class); }
        @Bean MemberRepository members() { return mock(MemberRepository.class); }
        @Bean SecurityErrorWriter errors() { return new SecurityErrorWriter(new ObjectMapper()); }
        @Bean SyncEndpoint endpoint() { return new SyncEndpoint(); }
    }
    @RestController
    @TestComponent
    static class SyncEndpoint {
        @RequestMapping(value="/api/admin/missing-sync", method={RequestMethod.GET,RequestMethod.POST})
        public String sync() { return "ok"; }
    }
}
