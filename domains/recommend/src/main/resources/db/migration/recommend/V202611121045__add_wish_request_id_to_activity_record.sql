-- #1284 조르기와 조르기 철회 활동 기록에 조르기 ID를 남긴다.
-- 선호 집계가 같은 조르기의 철회 기록을 찾아 그 조르기의 몫을 빼야 하기 때문이다.
-- 조르기는 주문 도메인의 것이므로 FK 없이 값으로만 참조한다.
ALTER TABLE recommend.activity_record ADD COLUMN wish_request_id BIGINT;

-- 철회 기록을 조르기 ID로 찾는 데 쓴다.
CREATE INDEX idx_activity_record_wish_request_id ON recommend.activity_record (wish_request_id);
