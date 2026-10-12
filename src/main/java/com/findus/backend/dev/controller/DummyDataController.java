package com.findus.backend.dev.controller;

import com.findus.backend.dev.dto.DummyDataResponse;
import com.findus.backend.dev.service.DummyDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@ConditionalOnProperty(name = "app.dummy.enabled", havingValue = "true")
@Tag(name = "개발용 더미 데이터", description = "로컬에서만 사용하는 테스트 데이터 생성")
public class DummyDataController {
    private final DummyDataService service;
    public DummyDataController(DummyDataService service) { this.service = service; }
    @PostMapping("/api/dev/dummy-data")
    @Operation(summary = "고정 더미 데이터 생성", description = "회원 5명·게시글 36개·댓글 72개를 최초 한 번 생성합니다. "
            + "재요청은 기존 데이터를 유지합니다. 로그인 없이 실행할 수 있지만 CSRF 토큰은 필요합니다.")
    public DummyDataResponse create() { return service.createPersistent(); }
}
