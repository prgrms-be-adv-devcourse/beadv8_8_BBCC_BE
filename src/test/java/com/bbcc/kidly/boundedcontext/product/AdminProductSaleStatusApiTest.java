package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 운영 정책을 어긴 상품을 판매중지하거나 다시 여는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 판매자 등록 API로 만든다. 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class AdminProductSaleStatusApiTest {

    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String ADMIN_PRODUCTS_PATH = "/api/v1/admin/products";
    private static final long SELLER_ID = 7L;
    private static final long ADMIN_ID = 1L;
    private static final Map<String, Object> ON_SALE = Map.of("status", "ON_SALE");
    private static final Map<String, Object> STOPPED = Map.of("status", "STOPPED");

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
        productId = client.post().uri(SELLER_PRODUCTS_PATH)
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
    @DisplayName("SALE-11 · SALE-08 관리자가 판매중 상품에 STOPPED를 보내면 판매중지와 관리자 판매중지 표시가 되고, 상품 정보 변경됨이 한 번 나간다")
    void stopSaleByAdmin() {
        startSaleBySeller();

        changeSaleStatusByAdmin(tokens.admin(ADMIN_ID), STOPPED)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("STOPPED");
        assertThat(storedStoppedByAdmin()).isTrue();
        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().status()).isEqualTo(ProductStatus.STOPPED));
    }

    // 판매자의 판매 시작 요청으로 판매중으로 바꾸고, 그때 나간 이벤트는 지운다
    private void startSaleBySeller() {
        client.put().uri(SELLER_PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(ON_SALE)
            .exchange()
            .expectStatus().isOk();
        eventRecorder.clear();
    }

    private RestTestClient.ResponseSpec changeSaleStatusByAdmin(String token, Map<String, Object> body) {
        return client.put().uri(ADMIN_PRODUCTS_PATH + "/{productId}/sale-status", productId)
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

    private boolean storedStoppedByAdmin() {
        return jdbcTemplate.queryForObject(
            "select is_stopped_by_admin from product.product where id = ?", Boolean.class, Long.valueOf(productId));
    }
}
