-- Access Token 즉시 무효화: 회원별로 이 시각 전에 발급된 Access Token을 거절한다 (#489)
-- Access Token 유효 기간이 지난 행은 앱이 주기적으로 지운다
CREATE TABLE member.access_token_revocation (
    member_id  bigint                         NOT NULL PRIMARY KEY,
    revoked_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);
CREATE INDEX idx_access_token_revocation_revoked_at ON member.access_token_revocation (revoked_at);
