-- #714 상품 복사본에 product.changed의 브랜드 · 설명 · 사이즈별 실측을 더한다.
-- 브랜드와 설명은 새 칸이 없는 예전 메시지로 만든 복사본도 있어 null을 허용한다.
ALTER TABLE recommend.replica_product ADD COLUMN brand character varying(255);
ALTER TABLE recommend.replica_product ADD COLUMN description text;

-- 사이즈별 실측 줄(복사본 하나에 사이즈마다 한 줄). measurements의 키는 실측 항목 코드, 값은 cm다.
-- 복사본의 @ElementCollection 표라 기존 replica_product_size처럼 복사본 FK만 둔다.
CREATE TABLE recommend.replica_product_size_spec (
    product_id bigint NOT NULL,
    size character varying(20) NOT NULL,
    min_height_cm integer,
    max_height_cm integer,
    measurements jsonb NOT NULL
);

ALTER TABLE ONLY recommend.replica_product_size_spec
    ADD CONSTRAINT replica_product_size_spec_product_id_fkey FOREIGN KEY (product_id) REFERENCES recommend.replica_product(product_id);
