-- 소프트 삭제된 행까지 유니크하게 유지하여 재등록은 기존 행을 복원합니다.
CREATE TABLE findus.member_favorites (
    id UUID NOT NULL,
    member_id UUID NOT NULL,
    missing_person_id UUID NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT pk_member_favorites PRIMARY KEY (id),
    CONSTRAINT uk_member_favorites_member_missing UNIQUE (member_id, missing_person_id),
    CONSTRAINT fk_member_favorites_member FOREIGN KEY (member_id) REFERENCES findus.members(id),
    CONSTRAINT fk_member_favorites_missing FOREIGN KEY (missing_person_id) REFERENCES findus.missing_persons(id)
);
CREATE INDEX idx_member_favorites_active_created_id ON findus.member_favorites(member_id,created_at DESC,id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_member_favorites_missing ON findus.member_favorites(missing_person_id);
COMMENT ON TABLE findus.member_favorites IS '회원별 관심아동. 원본 분류 010만 등록하며 취소는 소프트 삭제';
COMMENT ON COLUMN findus.member_favorites.member_id IS 'JWT 로그인 정보에서 결정하는 회원 UUID';
COMMENT ON COLUMN findus.member_favorites.missing_person_id IS '경찰청 source_id가 아닌 우리 서비스 실종자 UUID';
COMMENT ON COLUMN findus.member_favorites.deleted_at IS '관심 취소 시각. 재등록하면 NULL로 복원';
