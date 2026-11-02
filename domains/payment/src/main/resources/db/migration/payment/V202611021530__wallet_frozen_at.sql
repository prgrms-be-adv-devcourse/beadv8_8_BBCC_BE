-- 회원 탈퇴로 지갑을 동결한 시각(#670). null이면 동결되지 않은 지갑이다.
-- 동결된 지갑은 충전 · 주문 결제를 받지 않고, 환불 · 정산 입금은 그대로 받는다. 잔액은 운영자가 관리자 조회로 보고 환급한다.
ALTER TABLE payment.wallet ADD COLUMN frozen_at timestamp(6) without time zone;
