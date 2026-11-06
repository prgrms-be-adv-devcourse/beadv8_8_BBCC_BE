-- 리뷰 부적절 콘텐츠 검사 상태. 리뷰는 저장하면 바로 공개(PENDING)이고, 검사에서 걸리면 숨긴다(HIDDEN, 관리자 판단 대기)
-- PENDING(검사 대기, 공개) · PASSED(통과, 공개) · HIDDEN(검사가 숨김) · CONFIRMED_HIDDEN(관리자 숨김 확정) · RESTORED(관리자가 다시 공개)
-- 이미 있던 리뷰는 검사하지 않고 통과(PASSED)로 채운다. 새 리뷰는 앱이 PENDING으로 넣는다
-- content_version은 글 · 사진이 바뀔 때마다 +1. 검사 결과는 읽을 때의 버전이 그대로일 때만 반영한다
-- 이미 칸이 있을 수 있는 DB(공유 로컬)에서도 실패하지 않게 IF NOT EXISTS로 더한다
ALTER TABLE community.review
    ADD COLUMN IF NOT EXISTS moderation_status character varying(20) NOT NULL DEFAULT 'PASSED'
        CONSTRAINT ck_review_moderation_status
            CHECK (moderation_status IN ('PENDING', 'PASSED', 'HIDDEN', 'CONFIRMED_HIDDEN', 'RESTORED')),
    ADD COLUMN IF NOT EXISTS moderation_reason character varying(20)
        CONSTRAINT ck_review_moderation_reason
            CHECK (moderation_reason IN ('PROFANITY', 'HATE', 'SEXUAL', 'VIOLENCE', 'ILLEGAL', 'PRIVACY', 'OTHER')),
    ADD COLUMN IF NOT EXISTS moderation_attempts integer NOT NULL DEFAULT 0
        CONSTRAINT ck_review_moderation_attempts CHECK (moderation_attempts >= 0),
    ADD COLUMN IF NOT EXISTS moderated_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS content_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS moderated_by_admin_id bigint;

-- 검사 대기 조회(상태 + id 순) · 관리자 상태별 목록
CREATE INDEX IF NOT EXISTS idx_review_moderation_status ON community.review (moderation_status, id);
