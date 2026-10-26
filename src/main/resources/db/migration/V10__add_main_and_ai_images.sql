-- 대표 원본은 처음에는 원본 순번이 가장 작은 활성 사진으로 지정합니다.
ALTER TABLE findus.missing_person_images
    ADD COLUMN is_main BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE findus.missing_person_images
SET is_main = TRUE
WHERE id IN (
    SELECT DISTINCT ON (missing_person_id) id
    FROM findus.missing_person_images
    WHERE deleted_at IS NULL
    ORDER BY missing_person_id, sort_order, id
);

CREATE UNIQUE INDEX uk_missing_images_main
    ON findus.missing_person_images(missing_person_id)
    WHERE is_main AND deleted_at IS NULL;
ALTER TABLE findus.missing_person_images
    ADD CONSTRAINT ck_missing_images_main_active CHECK (NOT is_main OR deleted_at IS NULL),
    ADD CONSTRAINT uk_missing_images_id_person UNIQUE (id, missing_person_id);

-- 입력 원본은 FK로 참조하고 content에는 AI가 생성한 결과만 저장합니다.
CREATE TABLE findus.missing_person_ai_images (
    id UUID NOT NULL CONSTRAINT pk_missing_person_ai_images PRIMARY KEY,
    missing_person_id UUID NOT NULL,
    source_image_id UUID NOT NULL,
    target_age INTEGER NOT NULL,
    content BYTEA NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INTEGER NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT fk_ai_images_person FOREIGN KEY (missing_person_id)
        REFERENCES findus.missing_persons(id) ON DELETE CASCADE,
    -- 다른 아동의 원본을 연결하는 오류를 DB에서도 차단합니다.
    CONSTRAINT fk_ai_images_source_person FOREIGN KEY (source_image_id, missing_person_id)
        REFERENCES findus.missing_person_images(id, missing_person_id),
    CONSTRAINT ck_ai_images_age CHECK (target_age BETWEEN 0 AND 200),
    CONSTRAINT ck_ai_images_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_ai_images_size CHECK (byte_size > 0 AND byte_size <= 10485760 AND octet_length(content) = byte_size),
    CONSTRAINT ck_ai_images_main_active CHECK (NOT is_main OR deleted_at IS NULL)
);

CREATE UNIQUE INDEX uk_ai_images_main ON findus.missing_person_ai_images(missing_person_id)
    WHERE is_main AND deleted_at IS NULL;
CREATE INDEX idx_ai_images_person_created ON findus.missing_person_ai_images(missing_person_id, created_at, id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_ai_images_source_person ON findus.missing_person_ai_images(source_image_id, missing_person_id);

COMMENT ON TABLE findus.missing_person_ai_images IS '공통으로 사용하는 AI 생성 결과. 실제 모습 확인 결과가 아님';
COMMENT ON COLUMN findus.missing_person_ai_images.source_image_id IS '생성에 사용한 경찰청 원본 사진 레코드. 원본 파일은 복제하지 않음';
COMMENT ON COLUMN findus.missing_person_ai_images.target_age IS '우리 서비스가 생성 시 지정한 목표 나이';
COMMENT ON COLUMN findus.missing_person_ai_images.content IS 'AI가 생성한 결과 사진. 최대 10MB';
COMMENT ON COLUMN findus.missing_person_ai_images.is_main IS '아동별 대표 AI 사진. 최초 저장 사진을 기본 선택';
COMMENT ON COLUMN findus.missing_person_images.is_main IS '아동별 대표 원본 사진. 기본은 첫 순번, 이후 명시적 선택 유지';
