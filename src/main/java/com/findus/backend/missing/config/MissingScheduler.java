package com.findus.backend.missing.config;

import com.findus.backend.missing.service.MissingBatchRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="app.missing.schedule-enabled",havingValue="true")
public class MissingScheduler {
    private static final Logger log=LoggerFactory.getLogger(MissingScheduler.class);
    private final MissingBatchRunner runner;
    public MissingScheduler(MissingBatchRunner runner) { this.runner=runner; }
    @Scheduled(cron="${app.missing.cron:0 0 4,16 * * *}",zone="Asia/Seoul")
    public void sync() {
        try {
            var result=runner.run();
            log.info("실종자 배치 종료: 실행번호={}, 상태={}, 수신건수={}",result.executionId(),result.status(),result.counts().received());
        } catch (RuntimeException e) { log.error("실종자 배치 시작에 실패했습니다. 설정과 실행 이력을 확인하세요."); }
    }
}
