-- 관리자 결제 요약이 결제일 범위로 합계를 낸다(#1216)
CREATE INDEX IF NOT EXISTS idx_payment_paid_at ON payment.payment (paid_at);
