-- 상품 복사본(상품당 1행). product.changed의 지금 값을 덮어쓰며, 리뷰 · 찜 화면에 상품 이름 · 썸네일 · 표시 상태를 보여 줄 때 쓴다
CREATE TABLE community.replica_product (
    product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    thumbnail_url character varying(255),
    display_status character varying(20) NOT NULL,
    source_occurred_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT replica_product_pkey PRIMARY KEY (product_id)
);
