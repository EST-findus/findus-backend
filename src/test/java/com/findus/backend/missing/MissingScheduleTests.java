package com.findus.backend.missing;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import com.findus.backend.missing.config.MissingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.support.CronExpression;
import static org.assertj.core.api.Assertions.assertThat;

class MissingScheduleTests {
    @Test
    void 한국시간_4시와_16시로_예약을_계산한다() {
        var schedule=MissingScheduler.class.getDeclaredMethods();
        var annotation=java.util.Arrays.stream(schedule).filter(m -> m.getName().equals("sync")).findFirst().orElseThrow()
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(annotation.zone()).isEqualTo("Asia/Seoul");
        String cron=annotation.cron();cron=cron.substring(cron.indexOf(':')+1,cron.length()-1);
        var expression=CronExpression.parse(cron);
        var before=ZonedDateTime.of(2026,10,13,3,59,59,0,ZoneId.of(annotation.zone()));
        var first=expression.next(before);var second=expression.next(first);
        assertThat(first.getHour()).isEqualTo(4);assertThat(second.getHour()).isEqualTo(16);
        assertThat(expression.next(second).getDayOfMonth()).isEqualTo(14);
    }
    @Test
    void 설정이_꺼져있으면_스케줄러가_등록되지_않는다() {
        try (var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("테스트설정",Map.of("app.missing.schedule-enabled",false)));
            context.register(MissingScheduler.class);context.refresh();
            assertThat(context.getBeansOfType(MissingScheduler.class)).isEmpty();
        }
    }
}
