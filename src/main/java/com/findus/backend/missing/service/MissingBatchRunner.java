package com.findus.backend.missing.service;

import java.util.UUID;
import com.findus.backend.missing.dto.BatchRunResponse;
import com.findus.backend.missing.dto.SyncCounts;
import com.findus.backend.missing.exception.MissingSyncException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.stereotype.Service;

@Service
public class MissingBatchRunner {
    private final JobOperator operator;
    private final Job safeDreamSyncJob;
    public MissingBatchRunner(JobOperator operator,Job safeDreamSyncJob) { this.operator=operator;this.safeDreamSyncJob=safeDreamSyncJob; }
    public BatchRunResponse run() {
        try {
            var execution=operator.start(safeDreamSyncJob,new JobParametersBuilder()
                    .addString("requestId",UUID.randomUUID().toString()).toJobParameters());
            var counts=new SyncCounts(0,0,0,0);
            for (var step:execution.getStepExecutions()) {
                var c=step.getExecutionContext();
                counts=new SyncCounts(c.getInt("received",0),c.getInt("inserted",0),c.getInt("updated",0),c.getInt("unavailable",0));
            }
            return new BatchRunResponse(execution.getId(),execution.getStatus().name(),counts);
        } catch (Exception e) { throw new MissingSyncException("배치 실행을 시작할 수 없습니다."); }
    }
}
