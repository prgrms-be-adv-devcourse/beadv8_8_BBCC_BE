ALTER TABLE product.product ADD COLUMN sale_started_at timestamp(6) without time zone;

-- 실제로 판매를 시작한 시각은 남아 있지 않아, 이미 판매를 시작한 상품은 만든 시각으로 채운다
UPDATE product.product SET sale_started_at = created_at WHERE status <> 'SALE_PENDING';
