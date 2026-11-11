package com.bbcc.kidly.shared.product.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
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
    @DisplayName("상품 정보 변경 이벤트의 옵션은 옵션 이름 · 판매 상태 · 옵션 값을 담고, 색상 · 소재 칸을 따로 두지 않는다")
    void optionCarriesNameSaleStatusAndValues() {
        RecordComponent[] components = ProductChangedEvent.Option.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName).containsExactly(
            "optionId", "optionName", "size", "price", "minHeightCm", "maxHeightCm", "soldOut", "saleStatus",
            "values");
        assertThat(components[7].getType()).isEqualTo(OptionSaleStatus.class);
    }

    @Test
    @DisplayName("상품 정보 변경 이벤트는 기존 칸 뒤에 브랜드 · 설명 · 사이즈별 실측 · 카테고리 경로 · 관리자 판매 중지 · 판매 시작 시각 순서로 담고, "
        + "실측은 사이즈 · 권장 키 · 실측 맵이다")
    void dataCarriesBrandDescriptionAndSizeSpecs() {
        assertThat(ProductChangedEvent.Data.class.getRecordComponents()).extracting(RecordComponent::getName)
            .containsExactly("productId", "sellerId", "name", "categoryId", "status", "thumbnailUrl", "createdAt",
                "sizeFree", "options", "infoValues", "brand", "description", "sizeSpecs", "categoryPath",
                "stoppedByAdmin", "saleStartedAt");
        assertThat(ProductChangedEvent.SizeSpec.class.getRecordComponents()).extracting(RecordComponent::getName)
            .containsExactly("size", "minHeightCm", "maxHeightCm", "measurements");
    }

    @Test
    @DisplayName("판매 시작 시각 칸이 생기기 전의 생성자로 만들면 판매 시작 시각은 null이다")
    void constructorWithoutSaleStartedAtFillsNull() {
        ProductChangedEvent.Data data = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(), null, null,
            List.of(), List.of(), false);

        assertThat(data.saleStartedAt()).isNull();
    }

    @Test
    @DisplayName("판매 시작 시각을 넣어 만들면 판매 시작 시각이 그 값이다")
    void carriesSaleStartedAt() {
        LocalDateTime saleStartedAt = NOW.minusDays(3);
        ProductChangedEvent.Data data = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(), null, null,
            List.of(), List.of(), false, saleStartedAt);

        assertThat(data.saleStartedAt()).isEqualTo(saleStartedAt);
    }

    @Test
    @DisplayName("항목 값 칸이 생기기 전의 생성자로 만들면 옵션 값과 상품 정보 값은 빈 목록이다")
    void constructorsWithoutAttributeValuesFillEmptyLists() {
        ProductChangedEvent.Option option = new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
            105, 115, false, OptionSaleStatus.ON_SALE);
        ProductChangedEvent.Data data = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(option));

        assertThat(option.values()).isEmpty();
        assertThat(data.infoValues()).isEmpty();
    }

    @Test
    @DisplayName("브랜드 · 설명 · 사이즈별 실측 칸이 생기기 전의 생성자로 만들면 브랜드와 설명은 null, 사이즈별 실측은 빈 목록이다")
    void constructorsWithoutBrandDescriptionAndSizeSpecs() {
        ProductChangedEvent.Data withoutInfoValues = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of());
        ProductChangedEvent.Data withInfoValues = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of());

        assertThat(List.of(withoutInfoValues, withInfoValues)).allSatisfy(data -> {
            assertThat(data.brand()).isNull();
            assertThat(data.description()).isNull();
            assertThat(data.sizeSpecs()).isEmpty();
        });
    }

    @Test
    @DisplayName("사이즈별 실측 칸이 없는 예전 메시지처럼 null을 받으면 사이즈별 실측은 빈 목록이다")
    void nullSizeSpecsBecomeEmptyList() {
        ProductChangedEvent.Data data = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(),
            null, null, null);

        assertThat(data.sizeSpecs()).isEmpty();
    }

    @Test
    @DisplayName("카테고리 경로의 원소는 id와 이름이다")
    void dataCarriesCategoryPath() {
        assertThat(ProductChangedEvent.CategoryPathEntry.class.getRecordComponents())
            .extracting(RecordComponent::getName).containsExactly("id", "name");
    }

    @Test
    @DisplayName("카테고리 경로 칸이 생기기 전의 생성자로 만들거나 예전 메시지처럼 null을 받으면 카테고리 경로는 빈 목록이다")
    void categoryPathIsEmptyWhenAbsent() {
        ProductChangedEvent.Data withoutInfoValues = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of());
        ProductChangedEvent.Data withInfoValues = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of());
        ProductChangedEvent.Data withSizeSpecs = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(),
            "브랜드", "설명", List.of());
        ProductChangedEvent.Data withNull = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(),
            null, null, List.of(), null);

        assertThat(List.of(withoutInfoValues, withInfoValues, withSizeSpecs, withNull))
            .allSatisfy(data -> assertThat(data.categoryPath()).isEmpty());
    }

    @Test
    @DisplayName("관리자 판매 중지 칸이 없는 예전 메시지처럼 null을 받으면 false다")
    void nullStoppedByAdminBecomesFalse() {
        ProductChangedEvent.Data data = new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 1L, ProductStatus.ON_SALE, null, NOW, false, List.of(), List.of(),
            null, null, List.of(), List.of(), null);

        assertThat(data.stoppedByAdmin()).isFalse();
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

    @Test
    @DisplayName("재고 부족 이벤트는 stock.deduction-failed이고 대상은 주문이다")
    void stockDeductionFailed() {
        StockDeductionFailedEvent event = stockDeductionFailedEvent();

        assertContract(event, "stock.deduction-failed", "900");
        assertThat(event.data().reason()).isEqualTo(StockDeductionFailureCode.OPTION_NOT_ON_SALE);
        assertThat(event.data().failures()).extracting(StockDeductionFailedEvent.Failure::orderItemId)
            .containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("재고 부족 이벤트는 주문 ID, 대표 사유, 주문상품별 사유를 담고, 사유는 세 값이다")
    void stockDeductionFailedFields() {
        assertThat(StockDeductionFailedEvent.Data.class.getRecordComponents())
            .extracting(RecordComponent::getName).containsExactly("orderId", "reason", "failures");
        assertThat(StockDeductionFailedEvent.Failure.class.getRecordComponents())
            .extracting(RecordComponent::getName).containsExactly("orderItemId", "reason");
        assertThat(StockDeductionFailureCode.values()).extracting(Enum::name).containsExactly(
            "OUT_OF_STOCK", "PRODUCT_NOT_ON_SALE", "OPTION_NOT_ON_SALE");
    }

    private static StockDeductionFailedEvent stockDeductionFailedEvent() {
        return new StockDeductionFailedEvent(EVENT_ID, NOW, new StockDeductionFailedEvent.Data(
            900L, StockDeductionFailureCode.OPTION_NOT_ON_SALE, List.of(
                new StockDeductionFailedEvent.Failure(11L, StockDeductionFailureCode.OPTION_NOT_ON_SALE),
                new StockDeductionFailedEvent.Failure(12L, StockDeductionFailureCode.OUT_OF_STOCK))));
    }

    private static void assertContract(DomainEvent event, String eventType, String aggregateId) {
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.producer()).isEqualTo("product");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.correlationId()).isEqualTo(aggregateId);
    }
}
