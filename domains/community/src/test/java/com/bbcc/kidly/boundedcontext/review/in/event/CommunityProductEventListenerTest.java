package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.ProductChanged;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CommunityProductEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusHours(1);

    @Mock
    private ProductReplicaRecorder recorder;

    @InjectMocks
    private CommunityProductEventListener listener;

    @Captor
    private ArgumentCaptor<ProductChanged> command;

    @Test
    @DisplayName("이벤트를 복사본 입력으로 바꿔 넘긴다: 상품 ID · 이름 · 썸네일 · 상태 이름 · 옵션 상태 · 발생 시각")
    void convertsEvent() {
        listener.onProductChanged(event(data(1L, "상품 A", ProductStatus.ON_SALE, List.of(
            option(OptionSaleStatus.ON_SALE, false), option(OptionSaleStatus.STOPPED, true)))));

        verify(recorder).record(command.capture());
        assertThat(command.getValue()).isEqualTo(new ProductChanged(1L, "상품 A", "https://img/1.png", true,
            List.of(new ProductReplicaRecorder.Option(true, true),
                new ProductReplicaRecorder.Option(false, false)), OCCURRED_AT));
    }

    @Test
    @DisplayName("상품 · 옵션 상태가 없으면(모르는 값을 읽은 경우) 판매 중 아님으로, 옵션 목록이 없으면 빈 목록으로 넘긴다")
    void nullStatusesAndOptions() {
        listener.onProductChanged(event(data(1L, "상품 A", null, null)));
        listener.onProductChanged(event(data(1L, "상품 A", ProductStatus.ON_SALE, List.of(option(null, false)))));

        verify(recorder, org.mockito.Mockito.times(2)).record(command.capture());
        assertThat(command.getAllValues().get(0).productOnSale()).isFalse();
        assertThat(command.getAllValues().get(0).options()).isEmpty();
        assertThat(command.getAllValues().get(1).options())
            .containsExactly(new ProductReplicaRecorder.Option(false, true));
    }

    @ParameterizedTest(name = "상품 {0} → 판매 중 {1}")
    @CsvSource(nullValues = "null", value = {"ON_SALE, true", "STOPPED, false", "SALE_PENDING, false",
        "null, false"})
    @DisplayName("상품 판매 상태는 '판매 중 여부'로만 바꿔 넘긴다(판매 중만 true, 중지 · 판매 전 · 모르는 값은 false)")
    void productStatusToOnSale(ProductStatus status, boolean expected) {
        listener.onProductChanged(event(data(1L, "상품 A", status, List.of())));

        verify(recorder).record(command.capture());
        assertThat(command.getValue().productOnSale()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "옵션 {0} · 품절 {1} → 판매 중 {2} · 재고 {3}")
    @CsvSource(nullValues = "null", value = {"ON_SALE, false, true, true", "ON_SALE, true, true, false",
        "STOPPED, false, false, true", "STOPPED, true, false, false", "null, false, false, true"})
    @DisplayName("옵션은 '판매 중 여부 · 재고 유무'로만 바꿔 넘긴다(판매 중 옵션만 true, 재고 = 품절 아님)")
    void optionToOnSaleAndInStock(OptionSaleStatus saleStatus, boolean soldOut, boolean onSale, boolean inStock) {
        listener.onProductChanged(event(data(1L, "상품 A", ProductStatus.ON_SALE, List.of(option(saleStatus,
            soldOut)))));

        verify(recorder).record(command.capture());
        assertThat(command.getValue().options()).containsExactly(new ProductReplicaRecorder.Option(onSale, inStock));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingValues")
    @DisplayName("복사본을 만들 수 없는 값이 없으면 WARN 한 줄을 남기고 넘기지 않는다")
    void ignoresMissingRequiredValue(String reason, ProductChangedEvent event, CapturedOutput output) {
        listener.onProductChanged(event);

        verifyNoInteractions(recorder);
        assertThat(output.getOut()).contains("상품 이벤트 무시", "event=product.changed", "reason=" + reason);
    }

    private static Stream<Arguments> missingValues() {
        return Stream.of(
            Arguments.of("missing-data", event(null)),
            Arguments.of("missing-product-id", event(data(null, "상품 A", ProductStatus.ON_SALE, List.of()))),
            Arguments.of("missing-product-name", event(data(1L, null, ProductStatus.ON_SALE, List.of()))),
            Arguments.of("missing-occurred-at", new ProductChangedEvent(UUID.randomUUID(), null,
                data(1L, "상품 A", ProductStatus.ON_SALE, List.of()))));
    }

    private static ProductChangedEvent event(ProductChangedEvent.Data data) {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, data);
    }

    private static ProductChangedEvent.Data data(Long productId, String name, ProductStatus status,
        List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent.Data(productId, 2L, name, 3L, status, "https://img/1.png",
            OCCURRED_AT.minusDays(1), false, options);
    }

    private static ProductChangedEvent.Option option(OptionSaleStatus saleStatus, boolean soldOut) {
        return new ProductChangedEvent.Option(10L, "색상: 네이비 / 사이즈: 110", "110", 25_000L, null, null, soldOut,
            saleStatus);
    }
}
