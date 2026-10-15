package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 판매대기 상품의 판매를 시작하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 등록 API로 판매대기로 만들고, 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class ProductSaleStartApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;

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

    private String productId;

    @BeforeEach
    void registerProductA() {
        SavedClothingTop saved = clothingTopSaver.save();
        productId = client.post().uri(PRODUCTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(productA(saved))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
        eventRecorder.clear();
    }

    @Test
    @DisplayName("SALE-03 · SALE-08 판매대기 상품의 판매를 시작하면 판매중이 되고, 커밋 뒤 상품 정보 변경됨이 한 번 나간다")
    void startSaleOfPendingProduct() {
        startSale(tokens.seller(SELLER_ID), Map.of("status", "ON_SALE"))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("ON_SALE")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("ON_SALE");
        List<ProductChangedEvent> events = eventRecorder.events(ProductChangedEvent.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.data().productId()).isEqualTo(Long.valueOf(productId));
            assertThat(event.data().status()).isEqualTo(ProductStatus.ON_SALE);
            // 등록 요청의 초기 재고가 110은 10개, 120은 0개라 120만 품절이다
            assertThat(event.data().options())
                .extracting(ProductChangedEvent.Option::size, ProductChangedEvent.Option::soldOut,
                    ProductChangedEvent.Option::saleStatus)
                .containsExactly(tuple("110", false, OptionSaleStatus.ON_SALE),
                    tuple("120", true, OptionSaleStatus.ON_SALE));
        });
    }

    private RestTestClient.ResponseSpec startSale(String token, Map<String, Object> body) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private String storedStatus() {
        return jdbcTemplate.queryForObject(
            "select status from product.product where id = ?", String.class, Long.valueOf(productId));
    }

    private long storedVersion() {
        return jdbcTemplate.queryForObject(
            "select version from product.product where id = ?", Long.class, Long.valueOf(productId));
    }
}
