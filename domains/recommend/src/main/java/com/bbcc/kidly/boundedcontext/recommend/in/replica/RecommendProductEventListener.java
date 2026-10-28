package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상품 정보 변경됨을 받아 상품 복사본 반영을 맡긴다.
 * 주문에 {@code ProductEventListener}가 있어서, 스프링 빈 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Component
@RequiredArgsConstructor
public class RecommendProductEventListener {

    private final RecommendReplicaProductService replicaProductService;

    @DomainEventListener
    public void on(ProductChangedEvent event) {
        replicaProductService.applyProductChanged(event);
    }
}
