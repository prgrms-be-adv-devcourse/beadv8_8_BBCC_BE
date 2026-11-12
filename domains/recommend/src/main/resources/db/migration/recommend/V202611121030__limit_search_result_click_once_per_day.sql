-- #1284 같은 회원 · 자녀(자녀를 고르지 않으면 회원) · 상품의 검색 결과 클릭은 같은 날 한 번만 남긴다.
-- 같은 상품을 여러 번 누를 때마다 한 줄씩 쌓이면 한 상품이 자녀의 취향 계산에서 무게를 독차지하기 때문이다.
-- 날짜는 occurred_at의 날짜다. 이 칸은 한국 시간으로 저장하므로 ::date도 한국 날짜다.

-- 클릭 기록은 이 마이그레이션 전에는 없으므로 쌓인 중복을 지우지 않는다.
-- 다른 활동 종류는 같은 날 같은 상품을 여러 번 남길 수 있어 색인에서 뺀다.
CREATE UNIQUE INDEX idx_activity_record_search_result_clicked_per_day
    ON recommend.activity_record (member_id, child_profile_id, product_id, (occurred_at::date)) NULLS NOT DISTINCT
    WHERE type = 'SEARCH_RESULT_CLICKED';
