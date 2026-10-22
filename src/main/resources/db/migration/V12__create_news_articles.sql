-- 관리자가 네이버 뉴스 검색 결과에서 직접 확인하고 승인한 기사만 저장합니다.
-- 검색 결과 자체와 기사 요약문·본문은 저장하지 않습니다.
CREATE TABLE findus.news_articles (
    id UUID NOT NULL CONSTRAINT pk_news_articles PRIMARY KEY,
    title VARCHAR(600) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    naver_url VARCHAR(2048),
    published_at TIMESTAMP(6) WITHOUT TIME ZONE,
    placement VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    approved_by UUID NOT NULL,
    note VARCHAR(2000),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT fk_news_articles_approver FOREIGN KEY (approved_by) REFERENCES findus.members(id),
    CONSTRAINT ck_news_articles_placement CHECK (placement IN ('HOME', 'MYPAGE', 'BOTH'))
);

-- 노출 중단(소프트 삭제)한 기사는 같은 원문으로 다시 승인할 수 있습니다.
CREATE UNIQUE INDEX uk_news_articles_original_url ON findus.news_articles(original_url) WHERE deleted_at IS NULL;
CREATE INDEX idx_news_articles_active ON findus.news_articles(created_at DESC) WHERE deleted_at IS NULL;

COMMENT ON TABLE findus.news_articles IS '관리자가 승인한 네이버 뉴스 기사. 행이 있으면 승인된 것이고 deleted_at은 노출 중단 시각';
COMMENT ON COLUMN findus.news_articles.title IS '네이버 검색 제목에서 HTML 태그·엔티티를 제거한 값';
COMMENT ON COLUMN findus.news_articles.original_url IS '언론사 원문 주소(네이버 originallink, 없으면 link)';
COMMENT ON COLUMN findus.news_articles.naver_url IS 'NULL 허용 - 네이버 뉴스 주소(link)';
COMMENT ON COLUMN findus.news_articles.published_at IS 'NULL 허용 - 네이버 pubDate를 UTC로 변환한 기사 작성 시각';
COMMENT ON COLUMN findus.news_articles.placement IS '노출 위치: HOME, MYPAGE 또는 BOTH';
COMMENT ON COLUMN findus.news_articles.expires_at IS 'NULL 허용 - 게시 종료 시각(UTC). 지나면 공개 목록에서 제외';
COMMENT ON COLUMN findus.news_articles.approved_by IS '승인한 관리자 회원 ID';
COMMENT ON COLUMN findus.news_articles.note IS 'NULL 허용 - 승인 메모(공개 API에는 노출하지 않음)';
COMMENT ON COLUMN findus.news_articles.created_at IS '승인 시각';
