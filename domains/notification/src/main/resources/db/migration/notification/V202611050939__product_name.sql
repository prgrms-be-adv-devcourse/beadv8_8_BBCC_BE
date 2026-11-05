-- 알림 문구에 쓸 상품 이름 복사본. product.changed의 지금 값을 상품당 한 행으로 덮어쓴다 (#851)
-- source_occurred_at: 마지막으로 반영한 이벤트의 발생 시각. 이보다 늦은 이벤트만 반영한다
CREATE TABLE notification.product_name (
    product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    source_occurred_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT product_name_pkey PRIMARY KEY (product_id)
);
