package com.findus.backend.missing.controller;

import java.util.List;
import java.util.Map;
import com.findus.backend.missing.dto.BatchRunResponse;
import com.findus.backend.missing.service.MissingBatchRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/missing-sync")
// TODO 배포 전: SecurityConfig에서 관리자 권한·CSRF 검증을 복원하고 Swagger의 bearerAuth 요구도 복원합니다.
@Tag(name="실종자 배치",description="MVP local 프로필: 비회원도 JWT·CSRF 없이 실행·이력 조회 가능. 그 외 환경은 관리자 전용입니다. 배포 전 local 예외를 제거해야 합니다.")
public class MissingBatchController {
    private final MissingBatchRunner runner;
    private final JdbcTemplate jdbc;
    public MissingBatchController(MissingBatchRunner runner,JdbcTemplate jdbc) { this.runner=runner;this.jdbc=jdbc; }
    @PostMapping
    @Operation(summary="실종자 수집·동기화 수동 실행",description="실종아동 010과 사진을 수집하여 완료까지 기다린 뒤 처리 건수·사진 부분 실패 수·실제 HTTP 요청 수를 반환합니다. 수 분 걸릴 수 있습니다. 전체 데이터 수집 또는 DB 오류 시 FAILED로 기존 DB를 유지하며 개별 사진 실패는 기존 사진을 보존합니다.")
    public BatchRunResponse run() { return runner.run(); }
    @GetMapping
    @Operation(summary="최근 배치 실행 결과",description="최근 20건의 상태·시각만 제공합니다. 원본 데이터·인증정보·상세 오류는 공개하지 않습니다.")
    public List<Map<String,Object>> history() {
        return jdbc.queryForList("SELECT e.job_execution_id AS execution_id,e.status,e.start_time,e.end_time FROM public.batch_job_execution e JOIN public.batch_job_instance i ON i.job_instance_id=e.job_instance_id WHERE i.job_name='safeDreamSyncJob' ORDER BY e.job_execution_id DESC LIMIT 20");
    }
}
