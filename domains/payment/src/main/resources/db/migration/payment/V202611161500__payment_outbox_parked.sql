-- #1389 보낼 수 없는 메시지 하나가 릴레이 전체를 막지 않게, 시도 상한을 넘었고 다른 메시지는 보내지는 메시지를 격리한다.
-- 격리됐거나 시도 상한에 닿은 메시지와 같은 키(message_key)의 뒤 메시지는 보내지 않는다(같은 키 순서 유지).
ALTER TABLE payment.payment_outbox ADD COLUMN parked_at timestamp(6) without time zone;

-- 릴레이가 같은 키의 앞선 안 보낸 메시지를 본다. 안 보낸 행만 담는다
CREATE INDEX idx_payment_outbox_unsent_key ON payment.payment_outbox USING btree (message_key, id)
    WHERE sent_at IS NULL;
