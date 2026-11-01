package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItemCancellation;

/**
 * 판매자 취소 기록 저장소(#655). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderItemCancellationRepository {

    OrderItemCancellation save(OrderItemCancellation cancellation);
}
