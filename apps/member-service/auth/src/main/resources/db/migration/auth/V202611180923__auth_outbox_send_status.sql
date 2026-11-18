-- 인증 아웃박스도 회원 · 판매자 · 자녀 프로필 아웃박스처럼 보낸 행을 바로 지우지 않고 전송 결과를 남긴다.
-- 지금까지는 보낸 행을 지웠으므로 남아 있는 행은 모두 아직 보내지 않은 것이다. sent_at NULL, attempt_count 0이 맞다.
ALTER TABLE auth.auth_outbox
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN last_error character varying(500),
    ADD COLUMN sent_at timestamp(6) without time zone,
    ADD CONSTRAINT auth_outbox_attempt_count_check CHECK (attempt_count >= 0);

-- 릴레이: 아직 보내지 않은 행을 id 순서로 읽는다
CREATE INDEX idx_auth_outbox_unsent ON auth.auth_outbox USING btree (id) WHERE sent_at IS NULL;

-- 정리: 보낸 지 보관 기간이 지난 행을 지운다
CREATE INDEX idx_auth_outbox_sent_at ON auth.auth_outbox USING btree (sent_at) WHERE sent_at IS NOT NULL;
