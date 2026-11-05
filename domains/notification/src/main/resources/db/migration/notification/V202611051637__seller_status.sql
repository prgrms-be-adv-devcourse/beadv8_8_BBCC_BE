-- 판매자 승인 · 정지 메일을 가리는 판매자 상태 복사본(판매자당 1행). seller.changed의 지금 값을 덮어쓴다
-- 상호명만 바뀌어도 같은 이벤트가 오므로, 이전 상태와 비교해 상태가 바뀐 때만 메일을 만든다
-- source_occurred_at: 마지막으로 반영한 이벤트의 발생 시각. 이보다 늦은 이벤트만 반영한다
CREATE TABLE notification.seller_status (
    seller_id bigint NOT NULL,
    status character varying(16) NOT NULL,
    source_occurred_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT seller_status_pkey PRIMARY KEY (seller_id)
);
