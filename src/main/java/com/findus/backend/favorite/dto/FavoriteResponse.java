package com.findus.backend.favorite.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.favorite.entity.MemberFavorite;
import com.findus.backend.missing.dto.MissingPersonResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="내 관심아동. 공개 중단·삭제·분류 변경 시 child는 null이며 개인정보를 제공하지 않습니다.")
public record FavoriteResponse(
        @Schema(description="관심 등록 UUID") UUID id,
        @Schema(description="우리 DB의 실종자 UUID. 등록·취소 URL에 사용합니다.") UUID missingPersonId,
        @Schema(description="최초 등록 시각. 재등록해도 유지하며 UTC입니다.") LocalDateTime savedAt,
        @Schema(description="AVAILABLE: 조회 가능, UNAVAILABLE: 공개 중단 또는 삭제, NOT_CHILD: 아동 분류에서 변경") FavoriteAvailability availability,
        @Schema(description="조회 가능한 아동만 포함하는 공개 정보. 이용 불가 시 null") MissingPersonResponse child) {
    public static FavoriteResponse from(MemberFavorite favorite) {
        var person=favorite.getMissingPerson();
        var availability=person.isDeleted() || !person.isSourceAvailable() ? FavoriteAvailability.UNAVAILABLE
                : !"010".equals(person.getCategory()) ? FavoriteAvailability.NOT_CHILD : FavoriteAvailability.AVAILABLE;
        return new FavoriteResponse(favorite.getId(),person.getId(),favorite.getCreatedAt(),availability,
                availability==FavoriteAvailability.AVAILABLE ? MissingPersonResponse.from(person) : null);
    }
}
