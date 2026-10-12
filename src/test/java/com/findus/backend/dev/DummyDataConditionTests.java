package com.findus.backend.dev;

import java.util.Map;
import com.findus.backend.dev.config.DummyDataStartupRunner;
import com.findus.backend.dev.controller.DummyDataController;
import com.findus.backend.dev.repository.DummyDataRepositoryImpl;
import com.findus.backend.dev.service.DummyDataServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** 잘못된 환경에서 공개 개발 도구가 등록되지 않는지 확인합니다. */
class DummyDataConditionTests {
    @Test
    void 로컬이어도_기능을_끄면_등록되지_않는다() {
        assertDisabled("local", false);
    }
    @Test
    void 로컬이_아니면_활성화_설정만으로_등록되지_않는다() {
        assertDisabled("prod", true);
    }
    @Test
    void 시작_갱신만_별도로_끌_수_있다() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("테스트설정",
                    Map.of("app.dummy.enabled",true,"app.dummy.startup-enabled",false)));
            context.register(DummyDataStartupRunner.class);
            context.refresh();
            assertThat(context.getBeansOfType(DummyDataStartupRunner.class)).isEmpty();
        }
    }
    private void assertDisabled(String profile, boolean enabled) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("테스트설정",
                    Map.of("app.dummy.enabled",enabled,"app.dummy.startup-enabled",true)));
            context.register(DummyDataServiceImpl.class,DummyDataRepositoryImpl.class,
                    DummyDataController.class,DummyDataStartupRunner.class);
            context.refresh();
            assertThat(context.getBeansOfType(DummyDataController.class)).isEmpty();
            assertThat(context.getBeansOfType(DummyDataServiceImpl.class)).isEmpty();
            assertThat(context.getBeansOfType(DummyDataStartupRunner.class)).isEmpty();
        }
    }
}
