package com.findus.backend.missing.config;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.BatchStatus;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MissingBatchMetrics implements JobExecutionListener {
    private final MeterRegistry registry;
    private final java.util.concurrent.ConcurrentHashMap<Long,io.micrometer.core.instrument.Timer.Sample> samples=new java.util.concurrent.ConcurrentHashMap<>();
    public MissingBatchMetrics(MeterRegistry registry,JdbcTemplate jdbc) {
        this.registry=registry;
        registry.gauge("findus.missing.last.success.timestamp",jdbc,
                j -> j.queryForObject("SELECT coalesce(extract(epoch FROM max(last_seen_at)),0) FROM findus.missing_persons",Double.class));
    }
    @Override
    public void beforeJob(JobExecution execution) { samples.put(execution.getId(),io.micrometer.core.instrument.Timer.start(registry)); }
    @Override
    public void afterJob(JobExecution execution) {
        var sample=samples.remove(execution.getId());
        if (sample!=null) sample.stop(registry.timer("findus.missing.sync.duration","status",execution.getStatus().name()));
        registry.counter("findus.missing.sync.runs","status",execution.getStatus().name()).increment();
        if (execution.getStatus()==BatchStatus.COMPLETED) {
            for (var step:execution.getStepExecutions()) {
                for (String kind:new String[]{"received","inserted","updated","unavailable"}) {
                    registry.counter("findus.missing.sync.records","kind",kind)
                            .increment(step.getExecutionContext().getInt(kind,0));
                }
            }
        }
    }
}
