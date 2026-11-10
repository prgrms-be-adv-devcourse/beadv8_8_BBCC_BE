-- 돈을 움직이는 정산 관리자 행위(지급 재요청 · 멈춘 실행 정리 · 수동 실행 · DLT 재처리)를 누가 · 언제 · 무엇에 했는지 남긴다.
-- 추가만 하고 고치거나 지우지 않는다. target_id는 행위에 따라 정산서 ID · 실행 ID · DLT ID다
CREATE TABLE settlement.settlement_admin_action (
    id bigserial PRIMARY KEY,
    admin_id bigint NOT NULL,
    action varchar(40) NOT NULL,
    target_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT settlement_admin_action_action_check
        CHECK (action IN ('PAYOUT_RETRY', 'RUN_RECOVER', 'RUN_MANUAL', 'DEAD_LETTER_REPROCESS'))
);

-- 관리자별 이력
CREATE INDEX idx_settlement_admin_action_admin_created ON settlement.settlement_admin_action (admin_id, created_at);
-- 대상별 이력
CREATE INDEX idx_settlement_admin_action_action_target ON settlement.settlement_admin_action (action, target_id);
