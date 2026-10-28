package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ProductEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Mock
    private ReplicaService replicaService;

    @InjectMocks
    private ProductEventListener listener;

    @Captor
    private ArgumentCaptor<ProductChangedCommand> command;

    @Test
    @DisplayName("이벤트를 커맨드로 바꿔 넘긴다: 상품 필드 · 옵션 전체 · 발생 시각")
    void convertsEvent() {
        listener.onProductChanged(event(ProductStatus.ON_SALE,
            new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                105, 115, false, OptionSaleStatus.ON_SALE),
            new ProductChangedEvent.Option(2L, "색상: 네이비 / 사이즈: 120", "120", 26_000L,
                115, 125, true, OptionSaleStatus.STOPPED)));

        verify(replicaService).applyProductChanged(command.capture());
        ProductChangedCommand sent = command.getValue();
        assertThat(sent.productId()).isEqualTo(1L);
        assertThat(sent.sellerId()).isEqualTo(2L);
        assertThat(sent.productName()).isEqualTo("상품 A");
        assertThat(sent.categoryId()).isEqualTo(1L);
        assertThat(sent.productStatus()).isEqualTo("ON_SALE");
        assertThat(sent.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(sent.options()).hasSize(2);
        assertThat(sent.options().get(0)).isEqualTo(new ProductChangedCommand.Option(
            1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L, false, "ON_SALE"));
        assertThat(sent.options().get(1)).isEqualTo(new ProductChangedCommand.Option(
            2L, "색상: 네이비 / 사이즈: 120", "120", 26_000L, true, "STOPPED"));
    }

    @Test
    @DisplayName("판매 상태 enum은 이름 문자열로 넘긴다")
    void convertsStatusToName() {
        listener.onProductChanged(event(ProductStatus.STOPPED,
            new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                null, null, false, OptionSaleStatus.ON_SALE)));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().productStatus()).isEqualTo("STOPPED");
    }

    @Test
    @DisplayName("옵션 판매 상태가 비어 있으면 null로 넘긴다")
    void passesMissingOptionSaleStatusAsNull() {
        listener.onProductChanged(event(ProductStatus.ON_SALE,
            new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                null, null, false, null)));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().options().get(0).saleStatus()).isNull();
    }

    @Test
    @DisplayName("사이즈 없는 옵션(size null)은 size를 null로 그대로 넘긴다")
    void passesNullSize() {
        listener.onProductChanged(event(ProductStatus.ON_SALE, option(1L, null)));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().options()).singleElement()
            .extracting(ProductChangedCommand.Option::size).isNull();
    }

    @Test
    @DisplayName("판매 상태가 비어 있으면 모르는 값(UNKNOWN)으로 넘겨 구매 불가가 되게 한다")
    void passesMissingStatusAsUnknown() {
        listener.onProductChanged(event(null, option(1L, "110")));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().productStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("옵션 목록이 비어 있으면(null) 예외 없이 빈 목록으로 넘긴다")
    void passesMissingOptionsAsEmpty() {
        listener.onProductChanged(new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            data(1L, 2L, "상품 A", ProductStatus.ON_SALE, null)));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().options()).isEmpty();
    }

    @Test
    @DisplayName("옵션 ID가 없는 옵션은 WARN을 남기고 그 옵션만 건너뛴다")
    void skipsOptionWithoutId(CapturedOutput output) {
        listener.onProductChanged(event(ProductStatus.ON_SALE, option(null, "110"), option(2L, "120")));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().options()).extracting(ProductChangedCommand.Option::optionId)
            .containsExactly(2L);
        assertThat(output).containsPattern("WARN.*productId=1.*reason=missing-option-id");
    }

    @Test
    @DisplayName("같은 옵션 ID가 두 번 오면 WARN을 남기고 마지막 값 하나만 처음 자리에 넘긴다")
    void keepsLastOfDuplicateOptionId(CapturedOutput output) {
        listener.onProductChanged(event(ProductStatus.ON_SALE,
            option(1L, "110"), option(2L, "120"), option(1L, "130")));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().options()).extracting(ProductChangedCommand.Option::optionId)
            .containsExactly(1L, 2L);
        assertThat(command.getValue().options().get(0).size()).isEqualTo("130");
        assertThat(output).containsPattern("WARN.*optionId=1.*reason=duplicate-option-id");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventsMissingRequiredValue")
    @DisplayName("필수 값(데이터 · 상품 ID · 판매자 ID · 상품명 · 발생 시각)이 없으면 WARN을 남기고 이벤트 전체를 건너뛴다")
    void skipsEventMissingRequiredValue(String reason, ProductChangedEvent event, CapturedOutput output) {
        listener.onProductChanged(event);

        verifyNoInteractions(replicaService);
        assertThat(output).containsPattern("WARN.*reason=" + reason);
    }

    static Stream<Arguments> eventsMissingRequiredValue() {
        List<ProductChangedEvent.Option> options = List.of(option(1L, "110"));
        return Stream.of(
            Arguments.of("missing-data", new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, null)),
            Arguments.of("missing-product-id", new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(null, 2L, "상품 A", ProductStatus.ON_SALE, options))),
            Arguments.of("missing-seller-id", new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, null, "상품 A", ProductStatus.ON_SALE, options))),
            Arguments.of("missing-product-name", new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                data(1L, 2L, null, ProductStatus.ON_SALE, options))),
            Arguments.of("missing-occurred-at", new ProductChangedEvent(UUID.randomUUID(), null,
                data(1L, 2L, "상품 A", ProductStatus.ON_SALE, options))));
    }

    private static ProductChangedEvent event(ProductStatus status, ProductChangedEvent.Option... options) {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, data(1L, 2L, "상품 A", status, List.of(options)));
    }

    private static ProductChangedEvent.Data data(Long productId, Long sellerId, String name, ProductStatus status,
        List<ProductChangedEvent.Option> options) {
        return new ProductChangedEvent.Data(productId, sellerId, name, 1L, status, "https://example.com/a.png",
            OCCURRED_AT.minusDays(1), false, options);
    }

    private static ProductChangedEvent.Option option(Long optionId, String size) {
        return new ProductChangedEvent.Option(optionId, "색상: 네이비", size, 25_000L, null, null, false,
            OptionSaleStatus.ON_SALE);
    }
}
