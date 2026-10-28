package com.bbcc.kidly.boundedcontext.order.domain.order;

/**
 * 결제대기 주문에 회원 · 주문상품 구성 · 주문상품 금액 · 총액 중 하나라도 다른 결제 완료가 왔다.
 * 결제 쪽 버그로만 생기는 불변식 위반이라 API 오류가 아니다. 메시지의 값은 로그용 코드(공백 없음)다.
 */
public class PaymentMismatchException extends RuntimeException {

    /**
     * 불일치 사유. 대조 순서(회원 → 구성 → 주문상품 금액 → 총액)에서 처음 걸린 하나만 쓴다.
     */
    public enum Reason {
        MEMBER_MISMATCH("member-mismatch"),
        ITEM_MISSING("item-missing"),
        ITEM_EXTRA("item-extra"),
        ITEM_AMOUNT_MISMATCH("item-amount-mismatch"),
        TOTAL_AMOUNT_MISMATCH("total-amount-mismatch");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;

    public PaymentMismatchException(Long orderId, Reason reason, String detail) {
        super("결제 불일치: orderId=" + orderId + " reason=" + reason.code() + " " + detail);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
