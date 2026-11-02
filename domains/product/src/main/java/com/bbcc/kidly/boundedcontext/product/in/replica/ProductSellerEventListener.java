package com.bbcc.kidly.boundedcontext.product.in.replica;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 판매자 정보 변경됨을 받아 판매자 복사본 반영을 맡긴다.
 * 회원 도메인에 SellerEventListener 빈이 이미 있어서 이름 앞에 Product를 붙였다.
 * Kafka 수신 스위치(kidly.product.kafka.consumer.enabled)가 false일 때만 만든다. 장애 때 Kafka 수신을 끄고 Spring으로 되돌리는
 * 용도이고, 테스트 설정도 false라 다른 도메인 테스트는 지금처럼 Spring 경로로 돈다. 켜져 있으면(기본) Kafka 리스너만 받는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.product.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class ProductSellerEventListener {

    private final ProductReplicaSellerService replicaSellerService;

    @DomainEventListener
    public void on(SellerChangedEvent event) {
        replicaSellerService.applySellerChanged(event);
    }
}
