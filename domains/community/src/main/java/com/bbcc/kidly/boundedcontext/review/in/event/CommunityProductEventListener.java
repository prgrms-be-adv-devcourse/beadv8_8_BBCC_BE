package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.Option;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.ProductChanged;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * product.changed를 받아 상품 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳: Kafka 리스너({@link CommunityProductEventKafkaListener}),
 * 스위치가 꺼졌을 때 Spring 수신({@link CommunitySpringEventReceiver}).
 * 처리 메서드는 부르는 쪽 트랜잭션에 합류하고(Spring 수신), 없으면 새로 연다(Kafka 리스너).
 */
@Component
@RequiredArgsConstructor
public class CommunityProductEventListener {

    private final ProductReplicaRecorder productReplicaRecorder;

    /**
     * 복사본 행을 만들 수 없는 값(데이터 · 상품 ID · 상품 이름 · 발생 시각)이 없으면 WARN 한 줄을 남기고 건너뛴다.
     */
    @Transactional
    public boolean onProductChanged(ProductChangedEvent event) {
        if (CommunityEventGuard.accept("상품", event, "productId", productIdOf(event), missingRequiredValue(event))) {
            productReplicaRecorder.record(toChanged(event));
            return true;
        }
        return false;
    }

    private static String missingRequiredValue(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.productId() == null) {
            return "missing-product-id";
        }
        if (data.name() == null) {
            return "missing-product-name";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static Long productIdOf(ProductChangedEvent event) {
        return event.data() == null ? null : event.data().productId();
    }

    /**
     * 상품 용어를 커뮤니티 값으로 바꾸는 곳은 여기 한 곳이다: 상품 판매 상태 → 판매 중 여부, 옵션 판매 상태 · 품절 → 판매 중 여부 ·
     * 재고 유무. 판매 중(ON_SALE)만 true이고 그 밖(중지 · 판매 전 · 모르는 값을 읽은 null)은 false다.
     */
    private static ProductChanged toChanged(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new ProductChanged(data.productId(), data.name(), data.thumbnailUrl(),
            data.status() == ProductStatus.ON_SALE, toOptions(data.options()), event.occurredAt());
    }

    private static List<Option> toOptions(List<ProductChangedEvent.Option> options) {
        if (options == null) {
            return List.of();
        }
        return options.stream()
            .filter(option -> option != null)
            .map(option -> new Option(option.saleStatus() == OptionSaleStatus.ON_SALE, !option.soldOut()))
            .toList();
    }
}
