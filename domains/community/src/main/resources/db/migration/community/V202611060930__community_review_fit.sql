-- 리뷰 사이즈 핏(작아요 · 잘 맞아요 · 커요, 선택)과 상품별 핏 분포
-- 이미 칸이 있는 DB에서도 실패하지 않게 IF NOT EXISTS로 더한다(칸이 있으면 같은 줄의 CHECK도 건너뛴다)
ALTER TABLE community.review
    ADD COLUMN IF NOT EXISTS fit character varying(20)
        CONSTRAINT ck_review_fit CHECK (fit IN ('SMALL', 'TRUE_TO_SIZE', 'LARGE'));

ALTER TABLE community.review_stats
    ADD COLUMN IF NOT EXISTS fit_small bigint NOT NULL DEFAULT 0
        CONSTRAINT ck_review_stats_fit_small CHECK (fit_small >= 0),
    ADD COLUMN IF NOT EXISTS fit_true_to_size bigint NOT NULL DEFAULT 0
        CONSTRAINT ck_review_stats_fit_true_to_size CHECK (fit_true_to_size >= 0),
    ADD COLUMN IF NOT EXISTS fit_large bigint NOT NULL DEFAULT 0
        CONSTRAINT ck_review_stats_fit_large CHECK (fit_large >= 0);
