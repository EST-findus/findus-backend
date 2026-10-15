package com.findus.backend.missing.controller;

import java.util.List;
import java.util.Map;
import com.findus.backend.missing.dto.BatchRunResponse;
import com.findus.backend.missing.service.MissingBatchRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/missing-sync")
@Tag(name="실종자 배치",description="ADMIN 전용. 수동 실행에는 JWT와 CSRF가 필요합니다.")
@SecurityRequirement(name="bearerAuth")
public class MissingBatchController {
    private final MissingBatchRunner runner;
    private final JdbcTemplate jdbc;
    public MissingBatchController(MissingBatchRunner runner,JdbcTemplate jdbc) { this.runner=runner;this.jdbc=jdbc; }
    @PostMapping
    @Operation(summary="실종자 수집·동기화 수동 실행",description="완료까지 기다린 뒤 상태와 처리 건수를 반환합니다. FAILED면 기존 DB를 유지합니다.")
    public BatchRunResponse run() { return runner.run(); }
    @GetMapping
    @Operation(summary="최근 배치 실행 결과",description="최근 20건의 상태·시각만 제공합니다. 원본 데이터·인증정보·상세 오류는 공개하지 않습니다.")
    public List<Map<String,Object>> history() {
        return jdbc.queryForList("SELECT e.job_execution_id AS execution_id,e.status,e.start_time,e.end_time FROM public.batch_job_execution e JOIN public.batch_job_instance i ON i.job_instance_id=e.job_instance_id WHERE i.job_name='safeDreamSyncJob' ORDER BY e.job_execution_id DESC LIMIT 20");
    }
}
