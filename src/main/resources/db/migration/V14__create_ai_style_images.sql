-- 대표 AI 사진에 머리 스타일을 입힌 결과를 미리 저장해 두고, 사용자가 고를 때 생성 없이 바로 보여줍니다.
-- 대표 선택·목표 나이의 의미가 섞이지 않도록 AI 사진 테이블과 분리합니다.
ALTER TABLE findus.missing_person_ai_images
    ADD CONSTRAINT uk_ai_images_id_person UNIQUE (id, missing_person_id);

CREATE TABLE findus.missing_person_ai_style_images (
    id UUID NOT NULL CONSTRAINT pk_missing_person_ai_style_images PRIMARY KEY,
    ai_image_id UUID NOT NULL,
    missing_person_id UUID NOT NULL,
    sex VARCHAR(1) NOT NULL,
    style_key VARCHAR(32) NOT NULL,
    content BYTEA NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INTEGER NOT NULL,
    options TEXT,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    -- 다른 아동의 AI 사진을 연결하는 오류를 DB에서도 차단합니다.
    CONSTRAINT fk_ai_styles_image_person FOREIGN KEY (ai_image_id, missing_person_id)
        REFERENCES findus.missing_person_ai_images(id, missing_person_id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_styles_sex CHECK (sex IN ('M', 'F')),
    CONSTRAINT ck_ai_styles_key CHECK (style_key ~ '^[a-z0-9_]{1,32}$'),
    CONSTRAINT ck_ai_styles_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_ai_styles_size CHECK (byte_size > 0 AND byte_size <= 10485760 AND octet_length(content) = byte_size)
);

-- 같은 AI 사진에 같은 스타일은 하나만 유지합니다. 일괄 생성을 다시 실행하면 이미 있는 조합은 건너뜁니다.
CREATE UNIQUE INDEX uk_ai_styles_image_style ON findus.missing_person_ai_style_images(ai_image_id, style_key)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_ai_styles_person ON findus.missing_person_ai_style_images(missing_person_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE findus.missing_person_ai_style_images IS 'AI 생성 결과에 머리 스타일을 입힌 예시. 실제 모습이 아님';
COMMENT ON COLUMN findus.missing_person_ai_style_images.sex IS '스타일 목록을 고른 기준 성별 (M | F)';
COMMENT ON COLUMN findus.missing_person_ai_style_images.style_key IS 'AI 서버 configs/hair_presets.yaml의 스타일 key';
COMMENT ON COLUMN findus.missing_person_ai_style_images.options IS 'AI 서버가 남긴 생성 정보(참조 사진, 배율 등 JSON 문자열)';
