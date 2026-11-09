-- 정산 금액 불변식을 DB에서도 지킨다. 코드(Settlement.create · SettlementItem.create)가 이미 보장하는 규칙만 건다.
-- 구매확정 뒤에는 환불 · 차감이 없어 지급액이 음수가 될 정상 흐름은 없다(#484)
ALTER TABLE settlement.settlement
    ADD CONSTRAINT settlement_total_sales_amount_check CHECK (total_sales_amount >= 0),
    ADD CONSTRAINT settlement_fee_amount_check CHECK (fee_amount >= 0),
    ADD CONSTRAINT settlement_settlement_amount_check
        CHECK (settlement_amount >= 0 AND settlement_amount = total_sales_amount - fee_amount),
    ADD CONSTRAINT settlement_fee_rate_check CHECK (fee_rate >= 0 AND fee_rate <= 1);

ALTER TABLE settlement.settlement_item
    ADD CONSTRAINT settlement_item_sales_amount_check CHECK (sales_amount >= 0);
