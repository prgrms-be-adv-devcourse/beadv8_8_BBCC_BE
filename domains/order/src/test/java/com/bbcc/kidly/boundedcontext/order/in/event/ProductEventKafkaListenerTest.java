package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.product.events 리스너(#541): eventType 헤더로 고르고 본문(공통 칸 7개 + data)을 이벤트로 읽어 지금 처리를 그대로 부른다.
 * 재고 2종은 실패를 주문 자체 보관에 남겼으면 처리 완료(정상 반환), 아니면 다시 던져 재시도 → DLT로 보낸다.
 * 매퍼는 앱(Boot)처럼 모르는 필드를 무시한다. 실제 앱 매퍼로 읽는 것은 Kafka 통합 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ProductEventKafkaListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String OCCURRED_AT = "2026-10-30T09:00:00";

    private final JsonMapper jsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Mock
    private ProductEventListener productEventListener;

    @Mock
    private StockEventListener stockEventListener;

    private ProductEventKafkaListener listener() {
        return new ProductEventKafkaListener(jsonMapper, productEventListener, stockEventListener);
    }

    @Test
    @DisplayName("product.changed는 본문을 읽어 상품 처리에 넘긴다")
    void productChanged() {
        listener().on(record("product.changed", "10", envelope("product.changed", "10",
            "{\"productId\":10,\"sellerId\":2,\"name\":\"상품 A\",\"categoryId\":1,\"status\":\"ON_SALE\","
                + "\"thumbnailUrl\":null,\"createdAt\":\"2026-10-29T09:00:00\",\"sizeFree\":false,"
                + "\"options\":[{\"optionId\":101,\"optionName\":\"110\",\"size\":\"110\",\"price\":25000,"
                + "\"minHeightCm\":null,\"maxHeightCm\":null,\"soldOut\":false,\"saleStatus\":\"ON_SALE\"}]}")));

        var captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(productEventListener).onProductChanged(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().occurredAt()).isEqualTo(LocalDateTime.parse(OCCURRED_AT));
        assertThat(captor.getValue().data().productId()).isEqualTo(10L);
        assertThat(captor.getValue().data().status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(captor.getValue().data().options()).singleElement()
            .satisfies(option -> assertThat(option.price()).isEqualTo(25_000L));
    }

    @Test
    @DisplayName("stock.deducted는 재고 차감됨 처리에 넘긴다")
    void stockDeducted() {
        listener().on(record("stock.deducted", "7", envelope("stock.deducted", "7", "{\"orderId\":7}")));

        var captor = ArgumentCaptor.forClass(StockDeductedEvent.class);
        verify(stockEventListener).onStockDeducted(captor.capture());
        assertThat(captor.getValue().data().orderId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("stock.deduction-failed는 재고 차감 실패 처리에 넘긴다")
    void stockDeductionFailed() {
        listener().on(record("stock.deduction-failed", "7", envelope("stock.deduction-failed", "7",
            "{\"orderId\":7,\"reason\":\"OUT_OF_STOCK\","
                + "\"failures\":[{\"orderItemId\":70,\"reason\":\"OUT_OF_STOCK\"}]}")));

        var captor = ArgumentCaptor.forClass(StockDeductionFailedEvent.class);
        verify(stockEventListener).onStockDeductionFailed(captor.capture());
        assertThat(captor.getValue().data().failures()).singleElement()
            .satisfies(failure -> assertThat(failure.reason()).isEqualTo(StockDeductionFailureCode.OUT_OF_STOCK));
    }

    @Test
    @DisplayName("주문이 받지 않는 종류는 본문을 읽지 않고 건너뛴다")
    void skipsOtherEventType() {
        listener().on(record("product.deleted", "10", "읽을 수 없는 본문"));

        verifyNoInteractions(productEventListener, stockEventListener);
    }

    @Test
    @DisplayName("모르는 enum 값은 읽기 실패(JacksonException)로 던지고 처리를 부르지 않는다")
    void unknownEnumFails() {
        assertThatThrownBy(() -> listener().on(record("stock.deduction-failed", "7", envelope(
            "stock.deduction-failed", "7", "{\"orderId\":7,\"reason\":\"NEW_REASON\",\"failures\":[]}"))))
            .isInstanceOf(JacksonException.class);

        verifyNoInteractions(stockEventListener);
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 읽기 실패(IllegalArgumentException)")
    void missingEventTypeFails() {
        var record = new ConsumerRecord<>("kidly.product.events", 0, 0L, "7", "{}");

        assertThatThrownBy(() -> listener().on(record)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재고 처리 실패를 보관했으면 정상 반환한다(처리 완료, 다음 메시지로)")
    void keptFailureCompletes() {
        doThrow(keptFailure()).when(stockEventListener).onStockDeducted(any());

        listener().on(record("stock.deducted", "7", envelope("stock.deducted", "7", "{\"orderId\":7}")));

        verify(stockEventListener).onStockDeducted(any());
    }

    @Test
    @DisplayName("재고 처리 실패를 보관하지 못했으면 다시 던진다(재시도 → DLT)")
    void unkeptFailureIsRethrown() {
        var failure = new DataIntegrityViolationException("보관 실패");
        doThrow(failure).when(stockEventListener).onStockDeductionFailed(any());

        assertThatThrownBy(() -> listener().on(record("stock.deduction-failed", "7", envelope(
            "stock.deduction-failed", "7", "{\"orderId\":7,\"reason\":\"OUT_OF_STOCK\",\"failures\":[]}"))))
            .isSameAs(failure);
    }

    @Test
    @DisplayName("상품 처리 예외는 그대로 던진다(보관이 없으므로 재시도 → DLT)")
    void productFailureIsRethrown() {
        var failure = new IllegalStateException("저장 실패");
        doThrow(failure).when(productEventListener).onProductChanged(any());

        assertThatThrownBy(() -> listener().on(record("product.changed", "10", envelope("product.changed", "10",
            "{\"productId\":10,\"sellerId\":2,\"name\":\"상품 A\",\"status\":\"ON_SALE\",\"sizeFree\":false,"
                + "\"options\":[]}"))))
            .isSameAs(failure);
    }

    // EventFailureKeeper가 보관에 성공한 뒤 다시 던지는 예외와 같은 모양
    private static RuntimeException keptFailure() {
        var failure = new DataIntegrityViolationException("주문 UPDATE 실패");
        try {
            EventFailureKeeper.run("stock.deducted", 7L, () -> {
                throw failure;
            }, error -> new OrderEventFailureResult(1L, "STOCK_DEDUCTED", 7L, List.of(), "PENDING", "x", 1,
                LocalDateTime.now(), LocalDateTime.now()));
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("보관 뒤 다시 던져야 한다");
    }

    static ConsumerRecord<String, String> record(String eventType, String key, String body) {
        var record = new ConsumerRecord<>("kidly.product.events", 0, 0L, key, body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        record.headers().add("eventId", EVENT_ID.toString().getBytes(UTF_8));
        return record;
    }

    // 보내는 쪽 규칙의 본문: 공통 칸 7개 + data
    static String envelope(String eventType, String key, String data) {
        return "{\"eventId\":\"" + EVENT_ID + "\",\"eventType\":\"" + eventType + "\",\"schemaVersion\":1,"
            + "\"occurredAt\":\"" + OCCURRED_AT + "\",\"producer\":\"product\",\"aggregateId\":\"" + key + "\","
            + "\"correlationId\":\"" + key + "\",\"data\":" + data + "}";
    }
}
