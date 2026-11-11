-- 알림함 안 읽은 개수 부하 측정 준비 데이터(100만 건). 로컬 notification 스키마에만 넣는다(운영 · dev DB에 넣지 않는다)
-- 받는 사람은 실데이터와 섞이지 않게 큰 고정 범위의 회원(MEMBER)만 쓴다(README "ID 범위"):
--   분산 회원 9500001~9510000(1만 명): 회원마다 80건, 그중 최근 8건이 안 읽음 → 80만 건
--   몰린 회원 9600001(1명, 주문이 많은 판매자): 20만 건, 그중 최근 18만 건이 안 읽음
-- dedup_key는 'loadtest:'로 시작한다. cleanup.sql은 받는 사람 범위로만 지운다
-- 다시 돌려도 안전하다(같은 dedup_key는 건너뛴다). 생성 시각은 보존 기간(90일) 안에 둔다
BEGIN;

INSERT INTO notification.inbox
    (recipient_type, recipient_id, type, title, body, link_path, dedup_key, read_at, created_at, updated_at)
SELECT 'MEMBER', 9500000 + m, 'ORDER_PAID', '주문이 결제되었어요', 'LOADTEST 주문 결제 알림', '/orders',
       'loadtest:' || (9500000 + m) || ':' || n,
       CASE WHEN n <= 72 THEN now() - interval '1 day' END,
       now() - (80 - n) * interval '1 day',
       now() - (80 - n) * interval '1 day'
FROM generate_series(1, 10000) AS m, generate_series(1, 80) AS n
ON CONFLICT (dedup_key) DO NOTHING;

INSERT INTO notification.inbox
    (recipient_type, recipient_id, type, title, body, link_path, dedup_key, read_at, created_at, updated_at)
SELECT 'MEMBER', 9600001, 'SELLER_ORDER_PAID', '새 주문이 들어왔어요', 'LOADTEST 판매자 주문 알림', '/seller/orders',
       'loadtest:9600001:' || n,
       CASE WHEN n <= 20000 THEN now() - interval '1 day' END,
       now() - (200000 - n) * interval '30 seconds',
       now() - (200000 - n) * interval '30 seconds'
FROM generate_series(1, 200000) AS n
ON CONFLICT (dedup_key) DO NOTHING;

COMMIT;

-- 실행 계획이 실제 데이터 분포를 보게 통계를 갱신하고, 인덱스만 읽는 스캔이 힙을 다시 읽지 않게 가시성 맵을 채운다
VACUUM ANALYZE notification.inbox;
