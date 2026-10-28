package com.bbcc.kidly.boundedcontext.payment.in.dto;

import java.util.Set;

/**
 * 토스 웹훅 본문. 결제 상태 변경(PAYMENT_STATUS_CHANGED)의 data는 토스 결제 객체이고, 여기서는 주문번호와 상태만 읽는다.
 * 토스 웹훅에는 서명이 없으므로 이 값은 어떤 결제를 다시 조회할지 고르는 데만 쓴다.
 */
public record TossWebhookRequest(String eventType, Data data) {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final Set<String> CANCELED_STATUSES = Set.of("CANCELED", "PARTIAL_CANCELED");

    public record Data(String orderId, String status) {
    }

    /**
     * 결제가 취소됐다는 상태 변경인지. 아니면 토스를 조회하지 않고 넘어간다.
     */
    public boolean isPaymentCancelled() {
        return PAYMENT_STATUS_CHANGED.equals(eventType)
            && data != null
            && data.orderId() != null
            && CANCELED_STATUSES.contains(data.status());
    }
}
