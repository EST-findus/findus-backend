-- 회원가입·로그인 전에 회원 저장 구조를 준비합니다. 비밀번호는 해시만 저장합니다.
CREATE TABLE findus.members (
    id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT ck_members_email CHECK (email <> '' AND email = lower(btrim(email))),
    CONSTRAINT ck_members_password_hash CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_members_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_members_nickname CHECK (btrim(nickname) <> ''),
    CONSTRAINT ck_members_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_members_deleted_status CHECK (deleted_at IS NULL OR status = 'INACTIVE')
);

-- PK와 UNIQUE가 인덱스를 만들므로 동일 컬럼에 인덱스를 중복 추가하지 않습니다.
COMMENT ON TABLE findus.members IS '이메일·비밀번호 기반 회원. 소프트 삭제 후에도 이메일 중복을 방지합니다.';
COMMENT ON COLUMN findus.members.id IS 'JPA에서 생성하는 UUID 회원 식별자';
COMMENT ON COLUMN findus.members.email IS '앞뒤 공백 제거 및 소문자 정규화된 로그인 이메일';
COMMENT ON COLUMN findus.members.password_hash IS '비밀번호 해시. 원문 비밀번호 저장 금지';
COMMENT ON COLUMN findus.members.name IS '회원 이름';
COMMENT ON COLUMN findus.members.nickname IS '화면 표시 닉네임. 중복 허용';
COMMENT ON COLUMN findus.members.role IS '권한: USER 또는 ADMIN';
COMMENT ON COLUMN findus.members.status IS '상태: ACTIVE 또는 INACTIVE';
COMMENT ON COLUMN findus.members.created_at IS 'UTC 기준 최초 저장 시간. JPA Auditing에서 기록';
COMMENT ON COLUMN findus.members.updated_at IS 'UTC 기준 마지막 수정 시간. JPA Auditing에서 기록';
COMMENT ON COLUMN findus.members.deleted_at IS 'UTC 기준 소프트 삭제 시간. NULL이면 삭제되지 않은 회원';
