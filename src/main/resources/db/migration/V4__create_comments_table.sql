-- 댓글은 별도 테이블에서 관리하며 게시글·회원 행은 함께 삭제하지 않습니다.
CREATE TABLE findus.comments (
    id UUID NOT NULL,
    post_id UUID NOT NULL,
    member_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES findus.posts(id),
    CONSTRAINT fk_comments_member FOREIGN KEY (member_id) REFERENCES findus.members(id),
    CONSTRAINT ck_comments_content CHECK (btrim(content, E' \t\r\n') <> '')
);

-- 게시글별 삭제되지 않은 댓글을 작성 순서대로 조회합니다.
CREATE INDEX idx_comments_active_post_created_id ON findus.comments (post_id, created_at, id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_post ON findus.comments (post_id);
CREATE INDEX idx_comments_member ON findus.comments (member_id);

COMMENT ON TABLE findus.comments IS '게시글 댓글. 소프트 삭제된 댓글과 삭제된 게시글의 댓글은 조회에서 제외';
COMMENT ON COLUMN findus.comments.id IS 'JPA에서 생성하는 UUID 댓글 ID';
COMMENT ON COLUMN findus.comments.post_id IS '댓글이 속한 게시글 ID';
COMMENT ON COLUMN findus.comments.member_id IS '댓글 작성자 회원 ID. 로그인 정보에서 결정';
COMMENT ON COLUMN findus.comments.content IS '일반 텍스트 댓글. 최대 1000자';
COMMENT ON COLUMN findus.comments.created_at IS 'UTC 기준 작성 시간';
COMMENT ON COLUMN findus.comments.updated_at IS 'UTC 기준 마지막 수정 시간';
COMMENT ON COLUMN findus.comments.deleted_at IS 'UTC 기준 삭제 시간. NULL이면 조회 가능';
