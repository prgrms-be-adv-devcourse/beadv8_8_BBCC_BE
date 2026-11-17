-- 찜 소유자(#1377). 회원 본인(MEMBER)과 자녀 계정(CHILD)이 각자 찜을 갖는다.
-- owner_id: MEMBER는 회원 ID, CHILD는 자녀 계정 ID. guardian_id: MEMBER는 본인 회원 ID, CHILD는 보호자 회원 ID(보호자 탈퇴 정리 기준).
-- member_id는 지우지 않고(배포 중 옛 앱 호환) 새 행에도 보호자 회원 ID를 넣는다.
-- 이미 칸 · 제약 · 인덱스가 있는 DB에서 다시 돌려도 같은 결과가 되게 쓴다
ALTER TABLE community.favorite
    ADD COLUMN IF NOT EXISTS owner_type character varying(10) NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN IF NOT EXISTS owner_id bigint,
    ADD COLUMN IF NOT EXISTS guardian_id bigint;

-- 기존 행은 모두 회원 본인 찜이다
UPDATE community.favorite
SET owner_id = member_id, guardian_id = member_id
WHERE owner_id IS NULL OR guardian_id IS NULL;

ALTER TABLE community.favorite
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN guardian_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_favorite_owner_type' AND connamespace = 'community'::regnamespace) THEN
        ALTER TABLE community.favorite
            ADD CONSTRAINT ck_favorite_owner_type CHECK (owner_type IN ('MEMBER', 'CHILD'));
    END IF;
END $$;

-- (소유자, 상품)당 찜 1개. 회원 7과 자녀 계정 7은 소유자 종류가 달라 겹치지 않는다
CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_owner_product ON community.favorite (owner_type, owner_id, product_id);
-- 내 찜 목록(최신순 커서)
CREATE INDEX IF NOT EXISTS idx_favorite_owner_created
    ON community.favorite (owner_type, owner_id, created_at DESC, id DESC);
-- 보호자 탈퇴 때 본인 · 자녀 찜을 함께 지운다
CREATE INDEX IF NOT EXISTS idx_favorite_guardian ON community.favorite (guardian_id);

ALTER TABLE community.favorite DROP CONSTRAINT IF EXISTS uk_favorite_member_product;
DROP INDEX IF EXISTS community.uk_favorite_member_product;
DROP INDEX IF EXISTS community.idx_favorite_member_created;

-- 지운 자녀 계정(child-account.changed deleted=true를 받은 계정). 그 뒤 그 계정의 찜 요청을 막는다(403)
CREATE TABLE IF NOT EXISTS community.deleted_child_account (
    account_id bigint NOT NULL,
    guardian_id bigint NOT NULL,
    deleted_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT deleted_child_account_pkey PRIMARY KEY (account_id)
);
