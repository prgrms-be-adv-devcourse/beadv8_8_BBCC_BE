-- 결제 통계 확인용 더미 데이터. 로컬 전용이다(dev는 운영이라 넣지 않는다).
-- 최근 90일(한국 시간)에 결제 20,000건을 흩어 넣는다. 결제 항목 1~3개, 실패 5% · 전액 환불 7% · 부분 환불 8%.
-- ID는 9천만 번대만 쓴다(결제 · 주문 · 환불 · 취소 90000001~, 결제 항목 · 주문상품 900000011~, 회원 · 지갑 90100001~90100500).
-- 다시 돌려도 안전하다: 먼저 같은 범위를 지운다. 지우기만 하려면 cleanup.sql.
BEGIN;

DELETE FROM payment.refund_item WHERE refund_id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.refund WHERE id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.payment_item WHERE payment_id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.payment WHERE id BETWEEN 90000001 AND 90020000;

SELECT setseed(0.42);

CREATE TEMP TABLE seed_payment ON COMMIT DROP AS
SELECT 90000000 + n AS id,
       90100001 + (n % 500) AS member_id,
       (now() AT TIME ZONE 'Asia/Seoul') - make_interval(secs => floor(random() * 90 * 86400)) AS at,
       floor(random() * 100)::int AS roll,
       1 + floor(random() * 3)::int AS item_count
FROM generate_series(1, 20000) AS n;

CREATE TEMP TABLE seed_item ON COMMIT DROP AS
SELECT p.id * 10 + k AS id,
       p.id AS payment_id,
       k,
       (99 + floor(random() * 800)::int) * 100 AS amount
FROM seed_payment p, generate_series(1, p.item_count) AS k;

INSERT INTO payment.payment (id, order_id, wallet_id, member_id, amount, refunded_amount, status, failure_code,
                             paid_at, created_at, updated_at)
SELECT p.id, p.id, p.member_id, p.member_id, s.total,
       CASE WHEN p.roll < 5 THEN 0 WHEN p.roll < 12 THEN s.total WHEN p.roll < 20 THEN s.first ELSE 0 END,
       CASE WHEN p.roll < 5 THEN 'FAILED' WHEN p.roll < 12 THEN 'REFUNDED'
            WHEN p.roll < 20 AND p.item_count > 1 THEN 'PARTIALLY_REFUNDED'
            WHEN p.roll < 20 THEN 'REFUNDED' ELSE 'COMPLETED' END,
       CASE WHEN p.roll < 5 THEN 'INSUFFICIENT_BALANCE' END,
       CASE WHEN p.roll >= 5 THEN p.at END,
       p.at, p.at
FROM seed_payment p
JOIN (SELECT payment_id, sum(amount) AS total, sum(amount) FILTER (WHERE k = 1) AS first
      FROM seed_item GROUP BY payment_id) s ON s.payment_id = p.id;

-- 실패 결제는 항목을 남기지 않는다(결제가 만들어지지 않은 것과 같다)
INSERT INTO payment.payment_item (id, payment_id, order_item_id, amount, refunded_amount, created_at, updated_at)
SELECT i.id, i.payment_id, i.id, i.amount,
       CASE WHEN p.roll BETWEEN 5 AND 11 OR (p.roll BETWEEN 12 AND 19 AND i.k = 1) THEN i.amount ELSE 0 END,
       p.at, p.at
FROM seed_item i JOIN seed_payment p ON p.id = i.payment_id
WHERE p.roll >= 5;

-- 환불은 결제 뒤 0~5일 사이, 지금을 넘지 않게
INSERT INTO payment.refund (id, payment_id, order_id, cancellation_id, amount, status, refunded_at, created_at, updated_at)
SELECT p.id, p.id, p.id, p.id, pay.refunded_amount, 'COMPLETED', r.at, r.at, r.at
FROM seed_payment p
JOIN payment.payment pay ON pay.id = p.id
CROSS JOIN LATERAL (SELECT least(p.at + make_interval(secs => floor(random() * 5 * 86400)),
                                 now() AT TIME ZONE 'Asia/Seoul') AS at) r
WHERE p.roll BETWEEN 5 AND 19;

INSERT INTO payment.refund_item (id, refund_id, payment_item_id, amount, created_at, updated_at)
SELECT pi.id, pi.payment_id, pi.id, pi.refunded_amount, r.refunded_at, r.refunded_at
FROM payment.payment_item pi JOIN payment.refund r ON r.id = pi.payment_id
WHERE pi.payment_id BETWEEN 90000001 AND 90020000 AND pi.refunded_amount > 0;

COMMIT;

SELECT status, count(*) AS payments, sum(amount) AS amount, sum(refunded_amount) AS refunded
FROM payment.payment WHERE id BETWEEN 90000001 AND 90020000 GROUP BY status ORDER BY status;
