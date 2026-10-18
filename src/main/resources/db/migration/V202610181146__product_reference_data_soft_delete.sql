ALTER TABLE product.product_type ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.attribute ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.attribute_value ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.measurement_item ADD COLUMN deleted_at timestamp(6) without time zone;
ALTER TABLE product.category ADD COLUMN deleted_at timestamp(6) without time zone;
