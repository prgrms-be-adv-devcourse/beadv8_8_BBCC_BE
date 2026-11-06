-- 상품 복사본에 product.changed의 카테고리 경로를 더한다.
-- 경로는 대분류부터 종류 순서이고, 그 순서를 position에 저장한다. category_id는 상품 도메인의 ID라 FK 없이 값으로만 둔다.
-- 복사본의 @ElementCollection 표라 기존 replica_product_size_spec처럼 복사본 FK만 둔다.
CREATE TABLE recommend.replica_product_category_path (
    product_id bigint NOT NULL,
    position integer NOT NULL,
    category_id bigint NOT NULL,
    category_name character varying(255) NOT NULL
);

ALTER TABLE ONLY recommend.replica_product_category_path
    ADD CONSTRAINT replica_product_category_path_pkey PRIMARY KEY (product_id, position);

ALTER TABLE ONLY recommend.replica_product_category_path
    ADD CONSTRAINT replica_product_category_path_product_id_fkey FOREIGN KEY (product_id) REFERENCES recommend.replica_product(product_id);
