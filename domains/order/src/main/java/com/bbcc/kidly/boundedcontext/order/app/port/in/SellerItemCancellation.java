package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 배송 컨텍스트가 판매자의 발송 전 몫 취소를 주문에 반영한다(#655 · #641). 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 * 배송이 READY인지 · 판매자 본인인지 · 기능 스위치는 배송이 먼저 확인한다.
 */
public interface SellerItemCancellation {

    /**
     * 그 판매자의 주문상품을 취소하고(모두 취소되면 주문도 취소), 취소 기록 · 상태 이력(주문상품 · 주문)을 남기고
     * order-item.cancelled를 같은 트랜잭션에서 발행한다. 주문이 모두 취소돼도 order.cancelled는 보내지 않는다.
     *
     * @param reason 취소 사유 이름(OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER). 부르는 쪽이 이 값만 넘긴다
     * @param memo 판매자 메모. 없으면 null
     * @return 주문이 없거나, 결제완료가 아니거나, 그 판매자의 취소할 주문상품이 없으면 빈 값(아무것도 바꾸지 않음)
     */
    Optional<CancelledShare> cancelSellerItems(Long orderId, Long sellerId, String reason, String memo,
        DeliveryRecorder.HistoryActor actor, LocalDateTime now);

    /**
     * @param orderItemIds 이번에 취소한 주문상품(주문상품 순서)
     * @param orderCancelled 이 취소로 주문이 취소됐으면 true
     */
    record CancelledShare(Long cancellationId, List<Long> orderItemIds, boolean orderCancelled) {
    }
}
