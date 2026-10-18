ALTER TABLE product.product_type ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.attribute ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.attribute_value ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.measurement_item ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.category ADD COLUMN deleted_at timestamp(6) without time zone;

-- 목록 값 이름(같은 항목 안)과 카테고리 이름(같은 부모 아래)은 지우지 않은 행끼리만 겹치지 않는다
ALTER TABLE product.attribute_value DROP CONSTRAINT attribute_value_attribute_id_name_key;
CREATE UNIQUE INDEX attribute_value_attribute_id_name_live_key
    ON product.attribute_value USING btree (attribute_id, name) WHERE deleted_at IS NULL;

ALTER TABLE product.category DROP CONSTRAINT category_parent_id_name_key;
CREATE UNIQUE INDEX category_parent_id_name_live_key
    ON product.category USING btree (parent_id, name) WHERE deleted_at IS NULL;
