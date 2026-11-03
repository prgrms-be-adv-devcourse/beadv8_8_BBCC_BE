-- #704 빈 DB에서 kidly-app이 먼저 뜨면 kidly-app의 init이 추천 테이블을 만들지만, 상품 복사본 상세 칸 · 실측 표 마이그레이션(V202611021557)은 이제 추천 서비스에만 있어
-- 만들어지지 않는다. 그 뒤 서비스는 스키마가 비어 있지 않아 202611021557로 baseline을 잡고 이 마이그레이션을 건너뛴다.
-- 그런 DB에서만 V202611021557과 같게 만든다. 이미 있는 DB에서는 아무것도 하지 않는다.
ALTER TABLE recommend.replica_product ADD COLUMN IF NOT EXISTS brand character varying(255);
ALTER TABLE recommend.replica_product ADD COLUMN IF NOT EXISTS description text;

CREATE TABLE IF NOT EXISTS recommend.replica_product_size_spec (
    product_id bigint NOT NULL,
    size character varying(20) NOT NULL,
    min_height_cm integer,
    max_height_cm integer,
    measurements jsonb NOT NULL
);

-- PostgreSQL에는 ADD CONSTRAINT IF NOT EXISTS가 없어 제약이 없을 때만 만들도록 감싼다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'replica_product_size_spec_product_id_fkey'
          AND conrelid = 'recommend.replica_product_size_spec'::regclass
    ) THEN
        ALTER TABLE ONLY recommend.replica_product_size_spec
            ADD CONSTRAINT replica_product_size_spec_product_id_fkey FOREIGN KEY (product_id) REFERENCES recommend.replica_product(product_id);
    END IF;
END $$;
