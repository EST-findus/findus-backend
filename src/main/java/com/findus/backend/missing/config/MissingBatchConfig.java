package com.findus.backend.missing.config;

import com.findus.backend.missing.service.MissingSyncService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(MissingProperties.class)
public class MissingBatchConfig {
    @Bean
    public Step safeDreamSyncStep(JobRepository repository,PlatformTransactionManager transactionManager,MissingSyncService service) {
        return new StepBuilder("safeDreamSyncStep",repository).tasklet((contribution,context) -> {
            var counts=service.sync();
            var execution=context.getStepContext().getStepExecution();
            execution.getExecutionContext().putInt("received",counts.received());
            execution.getExecutionContext().putInt("inserted",counts.inserted());
            execution.getExecutionContext().putInt("updated",counts.updated());
            execution.getExecutionContext().putInt("unavailable",counts.unavailable());
            return RepeatStatus.FINISHED;
        },transactionManager).build();
    }
    @Bean
    public Job safeDreamSyncJob(JobRepository repository,Step safeDreamSyncStep,MissingBatchMetrics metrics) {
        return new JobBuilder("safeDreamSyncJob",repository).listener(metrics).start(safeDreamSyncStep).build();
    }
}
