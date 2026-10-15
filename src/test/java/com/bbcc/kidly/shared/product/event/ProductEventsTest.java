package com.bbcc.kidly.shared.product.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("3d8e5a2b-7c41-4f0e-b6a9-1e2f4c7d9a53");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 30);

    @Test
    @DisplayName("상품 정보 변경 이벤트는 product.changed이고 대상은 상품이다")
    void productChanged() {
        ProductChangedEvent event = new ProductChangedEvent(EVENT_ID, NOW, new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, "https://cdn.kidly.test/products/1.png", NOW, false,
            List.of(
                new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                    105, 115, false, OptionSaleStatus.ON_SALE),
                new ProductChangedEvent.Option(2L, "색상: 네이비 / 사이즈: 120", "120", 25_000L,
                    115, 125, true, OptionSaleStatus.ON_SALE))));

        assertContract(event, "product.changed", "1");
    }

    @Test
    @DisplayName("상품 정보 변경 이벤트의 옵션은 옵션 이름과 판매 상태를 담고, 색상 · 소재는 담지 않는다")
    void optionCarriesNameAndSaleStatus() {
        RecordComponent[] components = ProductChangedEvent.Option.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName).containsExactly(
            "optionId", "optionName", "size", "price", "minHeightCm", "maxHeightCm", "soldOut", "saleStatus");
        assertThat(components[7].getType()).isEqualTo(OptionSaleStatus.class);
    }

    @Test
    @DisplayName("사이즈 무관 상품은 권장 키 없이 옵션을 담는다")
    void sizeFreeProductHasNoHeightRange() {
        ProductChangedEvent.Option option = new ProductChangedEvent.Option(3L, "색상: 그레이 / 사이즈: FREE", "FREE", 30_000L,
            null, null, false, OptionSaleStatus.ON_SALE);

        ProductChangedEvent event = new ProductChangedEvent(EVENT_ID, NOW, new ProductChangedEvent.Data(
            2L, 2L, "아기 모자", 3L, ProductStatus.ON_SALE, null, NOW, true, List.of(option)));

        assertThat(event.data().sizeFree()).isTrue();
        assertThat(event.data().options().get(0).minHeightCm()).isNull();
        assertThat(event.data().options().get(0).maxHeightCm()).isNull();
    }

    @Test
    @DisplayName("재고 차감 이벤트는 stock.deducted이고 대상은 주문이다")
    void stockDeducted() {
        assertContract(new StockDeductedEvent(EVENT_ID, NOW, new StockDeductedEvent.Data(900L)),
            "stock.deducted", "900");
    }

    private static void assertContract(DomainEvent event, String eventType, String aggregateId) {
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.producer()).isEqualTo("product");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.correlationId()).isEqualTo(aggregateId);
    }
}
