-- #1389 보낼 수 없는 메시지 하나가 릴레이 전체를 막지 않게, 시도 상한을 넘었고 다른 메시지는 보내지는 메시지를 격리한다.
-- 격리된 메시지와 같은 키(message_key)의 뒤 메시지는 관리자가 다시 보내기 전까지 보내지 않는다(같은 키 순서 유지).
ALTER TABLE payment.payment_outbox ADD COLUMN parked_at timestamp(6) without time zone;

-- 릴레이가 같은 키에 격리된 메시지가 있는지 본다. 격리 행만 담는다
CREATE INDEX idx_payment_outbox_parked ON payment.payment_outbox USING btree (message_key)
    WHERE sent_at IS NULL AND parked_at IS NOT NULL;
