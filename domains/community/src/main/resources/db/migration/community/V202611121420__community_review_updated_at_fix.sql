-- 리뷰 수정 시각 보정(#1308). 검사 반영 · 관리자 조치가 updated_at을 바꾸던 문제를 고친 뒤, 기존 행을 작성자 수정 기준으로 되돌린다.
-- updated_at = 활동 기록의 마지막 작성자 수정(REVIEW_UPDATED) 시각, 없으면 작성 시각. 활동 기록(#1128) 전의 실제 수정은 기록이 없어 작성 시각이 된다.
-- 값이 결정적이라 다시 돌려도 결과가 같고, 바뀌는 행만 UPDATE한다
WITH target AS (
    SELECT r.id, COALESCE(u.last_updated_at, r.created_at) AS updated_at
    FROM community.review r
    LEFT JOIN (
        SELECT aggregate_id, max(occurred_at) AS last_updated_at
        FROM community.activity
        WHERE type = 'REVIEW_UPDATED'
        GROUP BY aggregate_id
    ) u ON u.aggregate_id = r.id
)
UPDATE community.review r
SET updated_at = t.updated_at
FROM target t
WHERE r.id = t.id
  AND r.updated_at IS DISTINCT FROM t.updated_at;
