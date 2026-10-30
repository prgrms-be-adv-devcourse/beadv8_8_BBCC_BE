package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 판매를 시작한 상품의 상품 정보 변경됨을 다시 보내는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 판매자 등록 API로 만든다. 같은 판매자가 SKU 코드가 같은 상품을 두 번 등록할 수 없어 상품마다 판매자를 다르게 한다.
 * 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class AdminProductChangedEventApiTest {

    private static final String PATH = "/api/v1/admin/products/changed-events";
    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long ADMIN_ID = 1L;
    private static final long ON_SALE_SELLER_ID = 7L;
    private static final long STOPPED_SELLER_ID = 8L;
    private static final long PENDING_SELLER_ID = 9L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("SALE-08 판매중 · 판매중지 상품마다 상품 정보 변경됨이 한 번씩 나가고, 판매대기 상품은 나가지 않으며, 보낸 상품 수를 돌려준다")
    void republishStartedSaleProducts() {
        Long onSaleProductId = register(ON_SALE_SELLER_ID);
        changeSaleStatus(ON_SALE_SELLER_ID, onSaleProductId, "ON_SALE");
        Long stoppedProductId = register(STOPPED_SELLER_ID);
        changeSaleStatus(STOPPED_SELLER_ID, stoppedProductId, "ON_SALE");
        changeSaleStatus(STOPPED_SELLER_ID, stoppedProductId, "STOPPED");
        register(PENDING_SELLER_ID);
        eventRecorder.clear();

        republish(tokens.admin(ADMIN_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.publishedCount").isEqualTo(2);

        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .extracting(event -> event.data().productId())
            .containsExactlyInAnyOrder(onSaleProductId, stoppedProductId);
    }

    @Test
    @DisplayName("다시 보낸 상품 정보 변경됨은 처음 보낸 이벤트와 eventId가 다르고 occurredAt이 더 늦다")
    void republishedEventHasNewEventIdAndLaterOccurredAt() {
        Long productId = register(ON_SALE_SELLER_ID);
        changeSaleStatus(ON_SALE_SELLER_ID, productId, "ON_SALE");
        ProductChangedEvent first = eventRecorder.events(ProductChangedEvent.class).getFirst();
        eventRecorder.clear();

        republish(tokens.admin(ADMIN_ID)).expectStatus().isOk();

        // 아웃박스는 같은 eventId를 한 번만 보내고, 받는 쪽은 저장된 occurredAt보다 늦은 이벤트만 반영한다
        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.eventId()).isNotEqualTo(first.eventId());
                assertThat(event.occurredAt()).isAfter(first.occurredAt());
            });
    }

    @Test
    @DisplayName("다시 보낸 상품 정보 변경됨은 상품 ID를 키로 아웃박스에 상품마다 한 줄씩 남는다")
    void republishStoresOutboxMessages() {
        Long firstProductId = register(ON_SALE_SELLER_ID);
        changeSaleStatus(ON_SALE_SELLER_ID, firstProductId, "ON_SALE");
        Long secondProductId = register(STOPPED_SELLER_ID);
        changeSaleStatus(STOPPED_SELLER_ID, secondProductId, "ON_SALE");
        eventRecorder.clear();

        republish(tokens.admin(ADMIN_ID)).expectStatus().isOk();

        // 판매 시작 때 남은 줄은 빼고, 다시 보낸 이벤트의 줄만 본다
        List<UUID> republishedEventIds = eventRecorder.events(ProductChangedEvent.class).stream()
            .map(ProductChangedEvent::eventId)
            .toList();
        assertThat(jdbcTemplate.queryForList(
            "select event_id, event_type, topic, message_key, sent_at from product.product_outbox"))
            .filteredOn(row -> republishedEventIds.contains(row.get("event_id")))
            .extracting("message_key", "event_type", "topic", "sent_at")
            .containsExactlyInAnyOrder(
                tuple(String.valueOf(firstProductId), "product.changed", "kidly.product.events", null),
                tuple(String.valueOf(secondProductId), "product.changed", "kidly.product.events", null));
    }

    private RestTestClient.ResponseSpec republish(String token) {
        return client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange();
    }

    private Long register(long sellerId) {
        String productId = client.post().uri(SELLER_PRODUCTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(productA(saved))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
        return Long.valueOf(productId);
    }

    private void changeSaleStatus(long sellerId, Long productId, String status) {
        client.put().uri(SELLER_PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", status))
            .exchange()
            .expectStatus().isOk();
    }
}
