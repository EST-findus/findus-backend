-- 사진 주소가 세션에 의존하므로 아동별로 수신한 실제 사진을 별도 테이블에 보관합니다.
CREATE TABLE findus.missing_person_images (
 id UUID NOT NULL CONSTRAINT pk_missing_person_images PRIMARY KEY,
 missing_person_id UUID NOT NULL,
 sort_order INTEGER NOT NULL,
 source_url VARCHAR(1024) NOT NULL,
 content_type VARCHAR(30) NOT NULL,
 content BYTEA NOT NULL,
 byte_size INTEGER NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
 CONSTRAINT fk_missing_images_person FOREIGN KEY (missing_person_id) REFERENCES findus.missing_persons(id) ON DELETE CASCADE,
 CONSTRAINT uk_missing_images_person_order UNIQUE (missing_person_id, sort_order),
 CONSTRAINT ck_missing_images_order CHECK (sort_order >= 0),
 CONSTRAINT ck_missing_images_type CHECK (content_type IN ('image/jpeg','image/png')),
 CONSTRAINT ck_missing_images_size CHECK (byte_size > 0 AND byte_size <= 2097152 AND octet_length(content) = byte_size),
 CONSTRAINT ck_missing_images_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_missing_images_visible ON findus.missing_person_images(missing_person_id,sort_order) WHERE deleted_at IS NULL;
COMMENT ON TABLE findus.missing_person_images IS '실종아동별 실제 수신 사진. 자료 출처: 경찰청';
COMMENT ON COLUMN findus.missing_person_images.source_url IS '출처 추적용 주소. 쿠키 세션에 의존하므로 화면에서 직접 사용하지 않음';
COMMENT ON COLUMN findus.missing_person_images.content IS '전용 쿠키 세션에서 수신한 사진 원본';
COMMENT ON COLUMN findus.missing_persons.photo_url IS '기존 공개 API 대표 사진 출처. 신규 화면은 missing_person_images의 이미지 조회 API를 사용';
