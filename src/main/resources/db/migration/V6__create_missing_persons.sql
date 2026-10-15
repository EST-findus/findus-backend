-- 경찰청 원본 식별자를 기준으로 저장하며 사진 내용은 복제하지 않습니다.
CREATE TABLE findus.missing_persons (
 id UUID PRIMARY KEY,
 source_id VARCHAR(30) NOT NULL CONSTRAINT uk_missing_persons_source UNIQUE,
 name VARCHAR(100) NOT NULL, category VARCHAR(3) NOT NULL,
 sex TEXT, occurrence_date TEXT, age INTEGER, age_now INTEGER,
 address TEXT, clothing TEXT, features TEXT, photo_url VARCHAR(500),
 source_available BOOLEAN NOT NULL DEFAULT TRUE,
 last_seen_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
 CONSTRAINT ck_missing_source CHECK (source_id ~ '^[0-9]{1,30}$'),
 CONSTRAINT ck_missing_category CHECK (category ~ '^[0-9]{3}$')
);
CREATE INDEX idx_missing_available_date ON findus.missing_persons(occurrence_date DESC,id DESC)
 WHERE source_available AND deleted_at IS NULL;
COMMENT ON TABLE findus.missing_persons IS '안전Dream 공개 실종 정보. 자료 출처: 경찰청';
COMMENT ON COLUMN findus.missing_persons.source_available IS '최근 전체 수집에 포함된 공개 여부. 발견 완료 여부가 아님';
COMMENT ON COLUMN findus.missing_persons.photo_url IS '경찰청 원본 사진 참조 주소. 이미지 파일 자체는 저장하지 않음';
