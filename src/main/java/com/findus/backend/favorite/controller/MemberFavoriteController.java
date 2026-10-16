package com.findus.backend.favorite.controller;

import java.util.UUID;
import com.findus.backend.auth.security.MemberPrincipal;
import com.findus.backend.favorite.dto.FavoriteResponse;
import com.findus.backend.favorite.dto.FavoritePageResponse;
import com.findus.backend.favorite.service.MemberFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/favorites")
@Tag(name="관심아동",description="로그인한 회원의 관심아동 관리. JWT 필요, 등록·취소는 CSRF도 필요합니다.")
@SecurityRequirement(name="bearerAuth")
public class MemberFavoriteController {
    private final MemberFavoriteService service;
    public MemberFavoriteController(MemberFavoriteService service) { this.service=service; }
    @PutMapping("/{missingPersonId}")
    @Operation(summary="관심아동 등록",description="우리 DB의 실종자 UUID를 넣습니다. 공개 중인 아동 코드 010만 가능하며 현재 나이는 제한하지 않습니다. 반복 등록은 같은 행을 유지하고 취소 후에는 기존 행을 복원합니다.")
    @ApiResponse(responseCode="200",description="등록 또는 기존 관심 반환")
    @ApiResponse(responseCode="400",description="아동 코드가 아닌 대상 또는 잘못된 UUID")
    @ApiResponse(responseCode="404",description="존재하지 않거나 공개 중단·삭제된 실종자")
    public FavoriteResponse register(@Parameter(hidden=true) @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description="GET /api/missing-persons 응답의 id(UUID). 경찰청 sourceId가 아닙니다.") @PathVariable UUID missingPersonId) {
        return service.register(principal.id(),missingPersonId);
    }
    @DeleteMapping("/{missingPersonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="관심아동 취소",description="관심 관계만 소프트 삭제합니다. 아동·회원 데이터는 유지하며 반복 취소도 204입니다.")
    @ApiResponse(responseCode="204",description="취소 완료 또는 이미 취소된 상태")
    public void cancel(@Parameter(hidden=true) @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID missingPersonId) { service.cancel(principal.id(),missingPersonId); }
    @GetMapping
    @Operation(summary="내 관심아동 목록",description="기본 10개, 최대 100개. 최초 등록 시각 내림차순이며 createdAt·id 정렬만 허용합니다. 공개 중단·삭제·분류 변경 시 child는 null이고 availability만 표시합니다. 재등록 시 최초 등록 시각은 유지합니다.")
    public FavoritePageResponse list(@Parameter(hidden=true) @AuthenticationPrincipal MemberPrincipal principal,
            @ParameterObject @PageableDefault(size=10,sort={"createdAt","id"},direction=Sort.Direction.DESC) Pageable pageable) {
        return service.findMine(principal.id(),pageable);
    }
}
