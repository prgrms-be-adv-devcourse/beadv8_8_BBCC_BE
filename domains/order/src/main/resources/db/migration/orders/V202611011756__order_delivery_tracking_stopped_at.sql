-- 택배 추적: 운송장 번호 형식 오류(Delivery Tracker BAD_REQUEST)로 조회를 멈춘 시각. null이면 조회 대상이다.
-- 판매자 수동 배송 완료는 이 값과 상관없이 할 수 있다.
ALTER TABLE orders.delivery ADD COLUMN tracking_stopped_at timestamp(6) without time zone;
