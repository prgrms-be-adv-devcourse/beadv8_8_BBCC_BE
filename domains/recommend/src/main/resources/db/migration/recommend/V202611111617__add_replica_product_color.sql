-- 상품 복사본에 옵션 색상을 남긴다. 홈 맛보기 AI가 이유를 쓸 때 상품 특성으로 쓴다.
-- 판매 가능 사이즈(replica_product_sellable_size)와 같은 방식이다. 이미 있는 복사본은 다음 상품 정보 변경됨을 받을 때 채워진다.
CREATE TABLE recommend.replica_product_color (
    product_id bigint NOT NULL,
    color character varying(100) NOT NULL,
    CONSTRAINT replica_product_color_pkey PRIMARY KEY (product_id, color),
    CONSTRAINT fk_replica_product_color_product FOREIGN KEY (product_id)
        REFERENCES recommend.replica_product (product_id)
);
