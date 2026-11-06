-- 승인 중 · 취소 중 충전을 찾는 쿼리(충전 정리 스케줄러, 멈춘 충전 지표 #786)가 충전 표 전체를 읽지 않게 한다
CREATE INDEX idx_charge_in_progress ON payment.charge USING btree (status, updated_at)
    WHERE status IN ('CONFIRMING', 'CANCELLING');
