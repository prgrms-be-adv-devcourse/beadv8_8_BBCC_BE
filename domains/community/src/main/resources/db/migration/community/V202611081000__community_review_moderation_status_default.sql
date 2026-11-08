-- 검사 상태 칸의 DB 기본값('PASSED')을 없앤다(#1042). 기본값은 검사 칸을 더할 때 이미 있던 리뷰를 통과로 채우는 데만 썼다.
-- 새 리뷰의 검사 대기(PENDING)는 엔티티가 넣고, JPA를 거치지 않고 칸 없이 넣으면 NOT NULL 위반으로 실패한다(검사 없이 통과로 들어가지 않게).
-- 기존 행 값 · NOT NULL · CHECK · 인덱스는 그대로다. DROP DEFAULT는 다시 돌려도 안전하다
ALTER TABLE community.review ALTER COLUMN moderation_status DROP DEFAULT;
