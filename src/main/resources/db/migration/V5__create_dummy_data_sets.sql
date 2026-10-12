-- 생성 완료 기록으로 재요청 시 고정 더미 데이터를 덮어쓰지 않습니다.
-- 실제 더미 행은 로컬 전용 Java 기능에서만 생성합니다.
CREATE TABLE findus.dummy_data_sets (
    name VARCHAR(30) PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT timezone('UTC', now())
);
COMMENT ON TABLE findus.dummy_data_sets IS '로컬 더미 데이터 세트 생성 완료 기록';
