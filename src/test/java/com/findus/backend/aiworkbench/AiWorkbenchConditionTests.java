package com.findus.backend.aiworkbench;

import java.util.Map;
import com.findus.backend.aiworkbench.config.AiWorkbenchSecurityConfig;
import com.findus.backend.aiworkbench.controller.AiWorkbenchController;
import com.findus.backend.aiworkbench.repository.AiWorkbenchSaveRepositoryImpl;
import com.findus.backend.aiworkbench.service.AiWorkbenchServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** 로컬에서 명시적으로 켜지 않으면 저장 API가 등록되지 않는지 확인합니다. */
class AiWorkbenchConditionTests {
    @Test void 로컬이어도_기능을_끄면_등록되지_않는다() { assertDisabled("local",false); }
    @Test void 로컬이_아니면_활성화_설정만으로_등록되지_않는다() { assertDisabled("prod",true); }

    private void assertDisabled(String profile,boolean enabled) {
        try (var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("테스트설정",Map.of("app.aiworkbench.enabled",enabled)));
            context.register(AiWorkbenchController.class,AiWorkbenchServiceImpl.class,AiWorkbenchSaveRepositoryImpl.class,AiWorkbenchSecurityConfig.class);
            context.refresh();
            assertThat(context.getBeansOfType(AiWorkbenchController.class)).isEmpty();
            assertThat(context.getBeansOfType(AiWorkbenchServiceImpl.class)).isEmpty();
            assertThat(context.getBeansOfType(AiWorkbenchSaveRepositoryImpl.class)).isEmpty();
        }
    }
}
