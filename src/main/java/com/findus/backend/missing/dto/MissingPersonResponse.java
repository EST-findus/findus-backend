package com.findus.backend.missing.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import com.findus.backend.missing.entity.MissingPerson;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="안전Dream 공개 실종 정보. 자료 출처: 경찰청")
public record MissingPersonResponse(UUID id,String sourceId,String name,String category,String sex,
        String occurrenceDate,Integer age,Integer ageNow,String address,String clothing,String features,
        @Schema(description="대표 사진의 백엔드 상대 경로. 기존 미동기화 데이터는 경찰청 원본 주소일 수 있습니다.") String photoUrl,
        List<MissingImageResponse> images,
        LocalDateTime lastSeenAt,String attribution) {
    public static MissingPersonResponse from(MissingPerson p) {
        return from(p,List.of());
    }
    public static MissingPersonResponse from(MissingPerson p,List<MissingImageResponse> images) {
        return from(p,images,images.isEmpty()?p.getPhotoUrl():images.get(0).url());
    }
    public static MissingPersonResponse from(MissingPerson p,List<MissingImageResponse> images,String mainPhotoUrl) {
        return new MissingPersonResponse(p.getId(),p.getSourceId(),p.getName(),p.getCategory(),p.getSex(),
                p.getOccurrenceDate(),p.getAge(),p.getAgeNow(),p.getAddress(),p.getClothing(),p.getFeatures(),
                mainPhotoUrl,List.copyOf(images),p.getLastSeenAt(),"자료 출처: 경찰청");
    }
}
