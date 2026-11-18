-- #1583 정수 점수 방식의 선호 집계 표와 활동 기록의 칸을 지운다.
-- 더보기와 홈 화면의 자녀 맞춤 추천은 친밀도 · 취향 벡터 표로 정렬하므로 이 표들은 채우거나 읽는 곳이 없다.
-- 표를 지우면 그 표의 id 시퀀스 · PK · UNIQUE 제약도 함께 지워진다.
DROP TABLE IF EXISTS recommend.child_category_preference;

DROP TABLE IF EXISTS recommend.child_brand_preference;

-- 집계에서 몫을 뺀 시각 칸과, 이 칸을 조건으로 쓰던 색인이다. 색인을 먼저 지워 무엇을 지우는지 드러낸다.
DROP INDEX IF EXISTS recommend.idx_activity_record_occurred_at_counted;

ALTER TABLE recommend.activity_record DROP COLUMN IF EXISTS uncounted_at;
