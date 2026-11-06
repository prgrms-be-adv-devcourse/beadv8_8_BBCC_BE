-- 판매자에게 관리자 판매 중지 · 옵션 품절을 알리려고, 상품 이름 복사본에 마지막으로 본 판매 상태를 더한다
-- status: 마지막으로 본 판매 상태. NULL은 이 칸을 더하기 전에 저장한 행
-- in_stock_option_ids: 품절이 아니던 옵션 ID. NULL은 아직 모름(이 칸을 더하기 전에 저장한 행)이라 다음 이벤트는 기준만 잡고 알리지 않는다
ALTER TABLE notification.product_name
    ADD COLUMN status character varying(20),
    ADD COLUMN in_stock_option_ids bigint[];
