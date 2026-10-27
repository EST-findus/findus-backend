package com.findus.backend.missing.dto;

import java.util.List;
import java.util.UUID;

/** 대표 원본 사진 검토 화면용 응답. dbHost로 지금 어느 DB를 고치는지 화면에서 확인합니다. */
public record MainPhotoReviewPage(String dbHost, int page, int totalPages, long totalElements, List<Person> content) {
    public MainPhotoReviewPage { content=List.copyOf(content); }

    public record Person(UUID id, String sourceId, String name, String sex, String occurrenceDate,
            Integer age, Integer ageNow, List<Image> images) {
        public Person { images=List.copyOf(images); }
    }

    public record Image(UUID id, int sortOrder, String url, boolean main) {
        public static Image from(MissingImageMetadata image) {
            return new Image(image.id(),image.sortOrder(),"/api/missing-persons/"+image.missingPersonId()+"/images/"+image.id(),image.main());
        }
    }
}
