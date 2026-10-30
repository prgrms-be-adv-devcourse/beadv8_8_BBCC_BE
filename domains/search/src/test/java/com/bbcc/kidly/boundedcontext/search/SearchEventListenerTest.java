package com.bbcc.kidly.boundedcontext.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.in.SearchProductEventListener;
import com.bbcc.kidly.boundedcontext.search.in.SearchSellerEventListener;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 · 판매자 토픽에서 받은 메시지를 eventType 헤더로 골라 색인에 넘기는지 본다.
 * 본문은 보내는 쪽(아웃박스 · 커밋 뒤 전송)과 같은 모양(공통 칸 7개 + data)이다.
 */
@ExtendWith(MockitoExtension.class)
class SearchEventListenerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    @Mock
    private SearchIndexService indexService;

    private SearchProductEventListener productListener;
    private SearchSellerEventListener sellerListener;

    @BeforeEach
    void setUp() {
        productListener = new SearchProductEventListener(indexService, JSON_MAPPER);
        sellerListener = new SearchSellerEventListener(indexService, JSON_MAPPER);
    }

    @Test
    @DisplayName("product.changed는 본문을 읽어 상품 색인에 넘긴다")
    void productChanged() {
        ProductChangedEvent event = productChangedEvent();

        productListener.on(record("kidly.product.events", "1", "product.changed", envelope(event, "product.changed",
            "product")));

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        then(indexService).should().applyProductChanged(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(event.eventId());
        assertThat(captor.getValue().occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(captor.getValue().data().name()).isEqualTo("여아 원피스");
        assertThat(captor.getValue().data().options()).singleElement()
            .satisfies(option -> assertThat(option.price()).isEqualTo(15_000L));
    }

    @Test
    @DisplayName("상품 토픽의 다른 종류(재고 차감 등)는 읽지 않고 건너뛴다")
    void skipOtherProductEvents() {
        productListener.on(record("kidly.product.events", "1", "stock.deducted", "받지 않는 종류라 읽지 않는 본문"));

        then(indexService).should(never()).applyProductChanged(any());
    }

    @Test
    @DisplayName("seller.changed는 본문을 읽어 판매자 색인에 넘긴다")
    void sellerChanged() {
        SellerChangedEvent event = new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(7L, "키들리 상점", SellerStatus.ACTIVE));

        sellerListener.on(record("kidly.seller.events", "7", "seller.changed", envelope(event, "seller.changed",
            "seller")));

        ArgumentCaptor<SellerChangedEvent> captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(indexService).should().applySellerChanged(captor.capture());
        assertThat(captor.getValue().data().businessName()).isEqualTo("키들리 상점");
        assertThat(captor.getValue().occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 JacksonException을 던진다(재시도 없이 DLT)")
    void unreadableBody() {
        assertThatThrownBy(() -> productListener.on(record("kidly.product.events", "1", "product.changed",
            "{\"data\":{\"productId\":\"숫자 아님\"}}")))
            .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 어떤 메시지인지 알 수 없어 IllegalArgumentException을 던진다(재시도 없이 DLT)")
    void missingEventType() {
        assertThatThrownBy(() -> sellerListener.on(record("kidly.seller.events", "7", null, "{}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventType");
    }

    private static ProductChangedEvent productChangedEvent() {
        ProductChangedEvent.Option option = new ProductChangedEvent.Option(10L, "기본", "110", 15_000L, null, null,
            false, OptionSaleStatus.ON_SALE, List.of(new ProductChangedEvent.AttributeEntry("COLOR", 3L, "분홍")));
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ProductChangedEvent.Data(1L, 7L,
            "여아 원피스", 3L, ProductStatus.ON_SALE, null, OCCURRED_AT.minusDays(1), false, List.of(option)));
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    private static String envelope(Object event, String eventType, String producer) {
        Map<String, Object> fields = JSON_MAPPER.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", eventType);
        body.put("schemaVersion", 1);
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", producer);
        body.put("aggregateId", "1");
        body.put("correlationId", "1");
        body.put("data", fields.get("data"));
        return JSON_MAPPER.writeValueAsString(body);
    }

    private static ConsumerRecord<String, String> record(String topic, String key, String eventType, String body) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, key, body);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(UTF_8));
        }
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        return record;
    }
}
