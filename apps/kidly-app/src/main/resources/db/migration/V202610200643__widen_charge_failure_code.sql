-- 토스 오류 코드가 50자를 넘는 것이 있어(NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN_BELOW_AMOUNT, 51자) 충전 · 충전 취소의 실패 코드를 늘린다 (#485)
ALTER TABLE payment.charge ALTER COLUMN failure_code TYPE character varying(100);
ALTER TABLE payment.charge_cancellation ALTER COLUMN failure_code TYPE character varying(100);
