-- #999 칸 예약의 결제 정리 시각: 결제 정리가 예약 칸을 정리(일부 차감이든 삭제든)하면 그 예약의 허용 주체 행 모두에 기록한다.
-- 예약한 쪽은 칸이 남는 일부 결제도 "이 예약으로 이미 산 것이 있다"로 알 수 있다(조르기 거절 · 철회 409). null = 아직 없음.
-- baseline 뒤 파일이라 다시 적용돼도 안전하게 둔다(OrderMigrationIntegrationTest).
ALTER TABLE orders.cart_reservation ADD COLUMN IF NOT EXISTS purchased_at timestamp(6) without time zone;
-- 결제 정리 표시와 예약 해제(조르기 거절 · 철회)가 겹치면 먼저 커밋한 쪽이 이긴다(@Version): 표시가 버전을 올려 늦은 해제의 삭제가 진다
ALTER TABLE orders.cart_reservation ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
