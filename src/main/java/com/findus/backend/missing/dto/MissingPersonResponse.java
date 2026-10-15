package com.findus.backend.missing.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import com.findus.backend.missing.entity.MissingPerson;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="안전Dream 공개 실종 정보. 자료 출처: 경찰청")
public record MissingPersonResponse(UUID id,String sourceId,String name,String category,String sex,
        String occurrenceDate,Integer age,Integer ageNow,String address,String clothing,String features,
        @Schema(description="경찰청 원본 사진 조회 주소. 사진이 없으면 null") String photoUrl,
        LocalDateTime lastSeenAt,String attribution) {
    public static MissingPersonResponse from(MissingPerson p) {
        return new MissingPersonResponse(p.getId(),p.getSourceId(),p.getName(),p.getCategory(),p.getSex(),
                p.getOccurrenceDate(),p.getAge(),p.getAgeNow(),p.getAddress(),p.getClothing(),p.getFeatures(),
                p.getPhotoUrl(),p.getLastSeenAt(),"자료 출처: 경찰청");
    }
}
