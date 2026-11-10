-- kidly-app이 상품 마이그레이션을 이미 적용한 DB(통합 앱 시절의 dev · 공유 로컬 DB)에서도 상품 서비스 Flyway가 이 파일을 다시 돌린다.
-- 서비스 baseline이 이 버전보다 앞서기 때문이다. 다시 돌아도 칸 추가가 실패하지 않고 실제 판매 시작 시각을 덮어쓰지 않게 한다.
ALTER TABLE product.product ADD COLUMN IF NOT EXISTS sale_started_at timestamp(6) without time zone;

-- 실제로 판매를 시작한 시각은 남아 있지 않아, 이미 판매를 시작한 상품은 만든 시각으로 채운다
UPDATE product.product SET sale_started_at = created_at WHERE status <> 'SALE_PENDING' AND sale_started_at IS NULL;
