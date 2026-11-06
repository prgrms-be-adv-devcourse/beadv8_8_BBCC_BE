-- 알림함 받는 사람을 로그인 주체(종류 + ID)로 바꾼다. 자녀 계정도 자기 알림함을 가진다
-- 회원 ID와 자녀 계정 ID는 ID 공간이 달라 같은 숫자일 수 있으므로, 모든 조회 · 변경은 종류와 ID를 함께 거른다
-- 기존 알림은 모두 회원 알림이다
ALTER TABLE notification.inbox ADD COLUMN recipient_type character varying(16) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE notification.inbox ALTER COLUMN recipient_type DROP DEFAULT;
ALTER TABLE notification.inbox
    ADD CONSTRAINT ck_inbox_recipient_type CHECK (recipient_type IN ('MEMBER', 'CHILD'));
ALTER TABLE notification.inbox RENAME COLUMN member_id TO recipient_id;

DROP INDEX notification.idx_inbox_member_id_created_at;
DROP INDEX notification.idx_inbox_member_id_unread;

-- 내 알림 최신순 목록
CREATE INDEX idx_inbox_recipient_created_at
    ON notification.inbox (recipient_type, recipient_id, created_at DESC);

-- 안 읽은 알림 개수
CREATE INDEX idx_inbox_recipient_unread
    ON notification.inbox (recipient_type, recipient_id) WHERE read_at IS NULL;
