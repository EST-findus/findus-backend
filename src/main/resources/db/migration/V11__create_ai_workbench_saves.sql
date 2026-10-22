-- Python 작업 화면에서 사람이 고른 AI 결과만 저장하고, 어떤 생성 결과에서 왔는지 남깁니다.
-- 로그인 없는 로컬 작업용이므로 승인자 신원은 기록하지 않습니다.
CREATE TABLE findus.ai_workbench_saves (
    id UUID NOT NULL CONSTRAINT pk_ai_workbench_saves PRIMARY KEY,
    ai_image_id UUID NOT NULL,
    missing_person_id UUID NOT NULL,
    source_image_id UUID NOT NULL,
    target_age INTEGER NOT NULL,
    result_id VARCHAR(64) NOT NULL,
    image_key VARCHAR(32) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    options TEXT,
    saved_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_workbench_saves_image FOREIGN KEY (ai_image_id)
        REFERENCES findus.missing_person_ai_images(id) ON DELETE CASCADE,
    -- 같은 생성 결과 이미지는 한 번만 저장합니다. 재시도는 이 행으로 기존 결과를 돌려줍니다.
    CONSTRAINT uk_ai_workbench_saves_request UNIQUE (result_id, image_key),
    CONSTRAINT ck_ai_workbench_saves_age CHECK (target_age BETWEEN 1 AND 100)
);

CREATE INDEX idx_ai_workbench_saves_person ON findus.ai_workbench_saves(missing_person_id, saved_at DESC);

COMMENT ON TABLE findus.ai_workbench_saves IS '로컬 AI 작업 화면에서 저장한 결과의 이력. 승인자 신원은 기록하지 않음';
COMMENT ON COLUMN findus.ai_workbench_saves.result_id IS 'Python AI 서버의 임시 결과 id. 24시간 뒤 원본 파일은 지워질 수 있음';
COMMENT ON COLUMN findus.ai_workbench_saves.image_key IS 'Python 결과 안의 이미지 키 (예: age30)';
COMMENT ON COLUMN findus.ai_workbench_saves.content_sha256 IS '같은 요청 번호로 다른 사진이 오는지 확인하는 결과 사진 해시';
COMMENT ON COLUMN findus.ai_workbench_saves.options IS '나이 변환·후처리 옵션과 모델 정보(JSON 문자열)';
