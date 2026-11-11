-- 상품 복사본의 카테고리 경로 칸마다 그 카테고리를 보여 줄 자녀 성별(ALL · GIRL)을 남긴다. 남아 자녀에게는 GIRL 카테고리의 상품을 추천하지 않는다.
-- 이미 있는 행은 ALL이고, 다음 상품 정보 변경됨을 받을 때 상품의 실제 값으로 채워진다.
-- 서비스 baseline이 이 버전보다 앞선 DB에서도 이 파일이 다시 돌 수 있으므로, 다시 돌아도 실패하지 않게 쓴다.
ALTER TABLE recommend.replica_product_category_path
    ADD COLUMN IF NOT EXISTS visible_gender character varying(10) NOT NULL DEFAULT 'ALL';
ALTER TABLE recommend.replica_product_category_path
    DROP CONSTRAINT IF EXISTS replica_product_category_path_visible_gender_check;
ALTER TABLE recommend.replica_product_category_path
    ADD CONSTRAINT replica_product_category_path_visible_gender_check CHECK (visible_gender IN ('ALL', 'GIRL'));
