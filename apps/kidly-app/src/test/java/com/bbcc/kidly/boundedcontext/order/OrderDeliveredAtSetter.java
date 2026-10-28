package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 준비용: 배송 건 없이 그 판매자 몫 주문상품에 배송 완료 시각을 원하는 값(과거 시각 등)으로 기록한다.
 * 운영 경로는 DeliveryService.complete(배송 완료와 한 트랜잭션)이고, 시각을 정할 수 없어 테스트에서만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class OrderDeliveredAtSetter {

    private final OrderRepository orderRepository;

    @Transactional
    public void recordDelivered(Long orderId, Long sellerId, LocalDateTime at) {
        orderRepository.findById(orderId).orElseThrow().recordDelivered(sellerId, at);
    }
}
