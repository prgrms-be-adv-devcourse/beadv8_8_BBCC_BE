package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주문이 받는 이벤트 7종(상품 · 재고 2 · 판매자 · 자녀 #541, 결제 완료 · 결제 실패 #594)을 Spring 이벤트로 받는다. Kafka 수신 스위치
 * (kidly.order.kafka.consumer.enabled)가 false일 때만 만든다: 장애 때 Kafka 수신을 끄고 Spring으로 되돌리는 용도이고,
 * 테스트 설정도 false라 다른 도메인 테스트는 지금처럼 Spring 경로로 돈다. 켜져 있으면(기본) Kafka 리스너만 받는다(이중 처리 없음).
 * 처리는 Kafka 리스너와 같은 처리 메서드를 그대로 부른다. 처리 메서드는 이 리스너의 새 트랜잭션에 합류한다(지금과 같은 한 트랜잭션).
 */
@Component
@ConditionalOnProperty(name = "kidly.order.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class SpringEventReceiver {

    private final ProductEventListener productEventListener;
    private final StockEventListener stockEventListener;
    private final SellerReplicaEventListener sellerReplicaEventListener;
    private final ChildProfileEventListener childProfileEventListener;
    private final PaymentEventListener paymentEventListener;

    @DomainEventListener
    public void onProductChanged(ProductChangedEvent event) {
        productEventListener.onProductChanged(event);
    }

    @DomainEventListener
    public void onStockDeducted(StockDeductedEvent event) {
        stockEventListener.onStockDeducted(event);
    }

    @DomainEventListener
    public void onStockDeductionFailed(StockDeductionFailedEvent event) {
        stockEventListener.onStockDeductionFailed(event);
    }

    @DomainEventListener
    public void onSellerChanged(SellerChangedEvent event) {
        sellerReplicaEventListener.onSellerChanged(event);
    }

    @DomainEventListener
    public void onChildChanged(ChildChangedEvent event) {
        childProfileEventListener.onChildChanged(event);
    }

    @DomainEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        paymentEventListener.onPaymentCompleted(event);
    }

    @DomainEventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        paymentEventListener.onPaymentFailed(event);
    }
}
