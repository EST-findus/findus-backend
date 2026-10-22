-- 서비스 문의(개발팀에 보내는 의견). 실종 제보는 받지 않습니다.
-- 답변 이메일은 답변 완료·종료 시 지웁니다.
CREATE TABLE findus.inquiries (
    id UUID NOT NULL CONSTRAINT pk_inquiries PRIMARY KEY,
    member_id UUID,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    message VARCHAR(1000) NOT NULL,
    email VARCHAR(254),
    email_agreed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    page VARCHAR(200),
    admin_memo VARCHAR(2000),
    answered_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT fk_inquiries_member FOREIGN KEY (member_id) REFERENCES findus.members(id) ON DELETE SET NULL,
    CONSTRAINT ck_inquiries_type CHECK (type IN ('BUG', 'FEATURE', 'CORRECTION', 'ETC')),
    CONSTRAINT ck_inquiries_status CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'ANSWERED', 'CLOSED'))
);

CREATE INDEX idx_inquiries_status_created ON findus.inquiries (status, created_at);
CREATE INDEX idx_inquiries_deleted ON findus.inquiries (deleted_at);

COMMENT ON TABLE findus.inquiries IS '서비스 문의. 소프트 삭제된 문의는 조회에서 제외';
COMMENT ON COLUMN findus.inquiries.member_id IS 'NULL 허용 - 로그인한 회원이 보낸 경우 회원 ID, 비회원이면 NULL';
COMMENT ON COLUMN findus.inquiries.type IS '문의 유형: BUG(오류 신고), FEATURE(기능 제안), CORRECTION(정보 정정 요청), ETC(기타)';
COMMENT ON COLUMN findus.inquiries.status IS '처리 상태: RECEIVED → IN_PROGRESS → ANSWERED / CLOSED';
COMMENT ON COLUMN findus.inquiries.message IS '문의 본문. 앞뒤 공백 제거 후 10~1000자, 줄바꿈 보존';
COMMENT ON COLUMN findus.inquiries.email IS 'NULL 허용 - 답변 받을 이메일. ANSWERED·CLOSED가 되면 삭제';
COMMENT ON COLUMN findus.inquiries.email_agreed_at IS 'NULL 허용 - UTC 기준 이메일 수집 동의 시각';
COMMENT ON COLUMN findus.inquiries.page IS 'NULL 허용 - 문의를 보낸 화면 경로(최대 200자)';
COMMENT ON COLUMN findus.inquiries.admin_memo IS 'NULL 허용 - 관리자 내부 메모(사용자에게 노출하지 않음)';
COMMENT ON COLUMN findus.inquiries.answered_at IS 'NULL 허용 - UTC 기준 처음 ANSWERED 또는 CLOSED가 된 시각';
COMMENT ON COLUMN findus.inquiries.created_at IS 'UTC 기준 접수 시간';
COMMENT ON COLUMN findus.inquiries.updated_at IS 'UTC 기준 마지막 수정 시간';
COMMENT ON COLUMN findus.inquiries.deleted_at IS 'UTC 기준 삭제 시간. NULL이면 조회 가능';
