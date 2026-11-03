-- #740 메시지 영구 실패를 격리한다. 같은 토픽 · 키의 후속 메시지는 격리 해제 전까지 보류한다.
ALTER TABLE orders.order_outbox
    ADD COLUMN quarantined_at timestamp(6) without time zone,
    ADD COLUMN last_retried_at timestamp(6) without time zone,
    ADD COLUMN last_retried_by bigint,
    ADD COLUMN retry_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT order_outbox_quarantine_unsent_check CHECK (quarantined_at IS NULL OR sent_at IS NULL),
    ADD CONSTRAINT order_outbox_retry_count_check CHECK (retry_count >= 0);

CREATE INDEX idx_order_outbox_quarantine_stream ON orders.order_outbox (topic, message_key, id)
    WHERE quarantined_at IS NOT NULL AND sent_at IS NULL;
CREATE INDEX idx_order_outbox_quarantined ON orders.order_outbox (id) WHERE quarantined_at IS NOT NULL;
