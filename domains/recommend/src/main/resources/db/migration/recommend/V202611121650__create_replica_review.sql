-- #1316 커뮤니티의 리뷰 변경됨 이벤트를 받아 리뷰의 사이즈 핏을 저장하는 표를 만든다. 다른 도메인 데이터의 복사본이라 원래 리뷰 ID를 PK로 쓴다.
-- 리뷰가 구매확정보다 먼저 도착해도 주문상품 ID로 이을 수 있도록 구매 기록과 따로 둔다. 주문상품과 상품은 다른 도메인의 ID라 FK 없이 값으로만 둔다.
-- 숨기거나 삭제한 리뷰도 행을 남기고 is_visible만 false로 둔다. 늦게 도착한 예전 공개 이벤트가 되살리지 못하게 이벤트 시각을 함께 저장한다.
CREATE TABLE recommend.replica_review (
    review_id bigint NOT NULL,
    order_item_id bigint NOT NULL,
    product_id bigint NOT NULL,
    fit character varying(20),
    is_visible boolean NOT NULL,
    event_occurred_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT replica_review_pkey PRIMARY KEY (review_id),
    CONSTRAINT replica_review_fit_check CHECK (((fit)::text = ANY ((ARRAY['SMALL'::character varying, 'TRUE_TO_SIZE'::character varying, 'LARGE'::character varying])::text[])))
);

-- 지난 구매에 그 구매의 리뷰 핏을 붙일 때 주문상품 ID로 찾는다.
CREATE INDEX idx_replica_review_order_item_id ON recommend.replica_review (order_item_id);

-- 상품별로 핏 수를 셀 때 상품 ID로 찾는다.
CREATE INDEX idx_replica_review_product_id ON recommend.replica_review (product_id);
