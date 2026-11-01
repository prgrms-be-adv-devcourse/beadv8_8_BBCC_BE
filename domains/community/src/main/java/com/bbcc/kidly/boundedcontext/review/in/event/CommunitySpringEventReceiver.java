package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티가 받는 이벤트를 Spring 이벤트로 받는다. Kafka 수신 스위치(kidly.community.kafka.consumer.enabled)가 false일 때만 만든다:
 * 장애 때 Kafka 수신을 끄고 Spring으로 되돌리는 용도이고, 테스트 설정도 false다. 켜져 있으면(기본) Kafka 리스너만 받는다(이중 처리 없음).
 * 처리는 Kafka 리스너와 같은 처리 메서드를 그대로 부른다. 처리 메서드는 이 리스너의 새 트랜잭션에 합류한다.
 */
@Component
@ConditionalOnProperty(name = "kidly.community.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class CommunitySpringEventReceiver {

    private final CommunityProductEventListener productEventListener;

    @DomainEventListener
    public void onProductChanged(ProductChangedEvent event) {
        productEventListener.onProductChanged(event);
    }
}
