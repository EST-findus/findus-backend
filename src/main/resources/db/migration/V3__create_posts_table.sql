-- 게시글과 소프트 삭제 상태를 한 테이블에서 관리합니다.
CREATE TABLE findus.posts (
    id UUID NOT NULL,
    member_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT pk_posts PRIMARY KEY (id),
    CONSTRAINT fk_posts_member FOREIGN KEY (member_id) REFERENCES findus.members(id),
    CONSTRAINT ck_posts_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_posts_content CHECK (btrim(content) <> '' AND char_length(content) <= 10000)
);

-- 기본 최신순 목록은 삭제되지 않은 글만 대상으로 조회합니다.
CREATE INDEX idx_posts_active_created_id ON findus.posts (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_member ON findus.posts (member_id);

COMMENT ON TABLE findus.posts IS '회원이 작성하는 일반 게시글. 소프트 삭제된 글은 조회에서 제외';
COMMENT ON COLUMN findus.posts.id IS 'JPA에서 생성하는 UUID 게시글 식별자';
COMMENT ON COLUMN findus.posts.member_id IS '작성자 회원 ID. 로그인 정보에서 결정';
COMMENT ON COLUMN findus.posts.title IS '게시글 제목. 최대 200자';
COMMENT ON COLUMN findus.posts.content IS '일반 텍스트 본문. 최대 10000자';
COMMENT ON COLUMN findus.posts.created_at IS 'UTC 기준 작성 시간';
COMMENT ON COLUMN findus.posts.updated_at IS 'UTC 기준 마지막 수정 시간';
COMMENT ON COLUMN findus.posts.deleted_at IS 'UTC 기준 삭제 시간. NULL이면 조회 가능';
