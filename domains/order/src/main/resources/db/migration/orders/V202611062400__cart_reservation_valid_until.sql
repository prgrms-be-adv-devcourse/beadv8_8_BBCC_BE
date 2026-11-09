-- #1008 장바구니 예약의 결제 가능 기한(장바구니의 일반 기능: 장바구니는 누가 정했는지 모른다). null이면 기한 없음.
-- 기한이 지난 예약 칸은 허용 주체라도 주문할 수 없다(409). 두 번 적용해도 안전하다
ALTER TABLE orders.cart_reservation ADD COLUMN IF NOT EXISTS valid_until timestamp(6) without time zone;
