-- Refresh Token 재사용 감지: 교체할 때 직전 해시와 교체 시각을 남긴다 (#476)
ALTER TABLE member.refresh_token ADD COLUMN previous_token_hash varchar(64);
ALTER TABLE member.refresh_token ADD COLUMN rotated_at timestamp(6) without time zone;
CREATE INDEX idx_refresh_token_previous_token_hash ON member.refresh_token (previous_token_hash);
