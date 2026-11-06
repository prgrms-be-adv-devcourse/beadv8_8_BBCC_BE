package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 등록 취소한 상품을 판매 상태 변경 · 상품 수정 · 재고 조정으로 바꾸려 하면 409로 거절하는지 HTTP 요청부터 응답까지 확인한다.
 * 상품은 등록 API로 만들고 판매를 시작한 뒤 등록 취소 API로 등록 취소 상태로 만든다. 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class ProductDelistedRejectApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String ADMIN_PRODUCTS_PATH = "/api/v1/admin/products";
    private static final long SELLER_ID = 7L;
    private static final long ADMIN_ID = 1L;
    private static final long OTHER_SELLER_ID = 8L;
    private static final String DELISTED_TITLE = "등록 취소한 상품입니다";
    private static final String DELISTED_DETAIL =
        "등록 취소한 상품은 판매 상태 · 상품 정보 · 재고를 바꿀 수 없습니다. 다시 팔려면 상품을 새로 등록해 주세요.";

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SavedClothingTop saved;

    private String productId;

    @BeforeEach
    void registerAndDelistProductA() {
        saved = clothingTopSaver.save();
        productId = client.post().uri(PRODUCTS_PATH)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(productA(saved))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
        changeSaleStatusBySeller("ON_SALE").expectStatus().isOk();
        client.put().uri(PRODUCTS_PATH + "/{productId}/delisting", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isOk();
        eventRecorder.clear();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ON_SALE", "STOPPED"})
    @DisplayName("SALE-15 등록 취소한 상품에 판매자가 판매 상태 변경을 보내면 409이고, 등록 취소로 남고 이벤트도 나가지 않는다")
    void rejectSaleStatusChangeBySeller(String status) {
        expectDelisted(changeSaleStatusBySeller(status), PRODUCTS_PATH + "/" + productId + "/sale-status");

        assertThat(storedStatus()).isEqualTo("DELISTED");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ON_SALE", "STOPPED"})
    @DisplayName("SALE-15 등록 취소한 상품에 관리자가 판매 상태 변경을 보내면 409이고, 등록 취소로 남고 이벤트도 나가지 않는다")
    void rejectSaleStatusChangeByAdmin(String status) {
        expectDelisted(
            client.put().uri(ADMIN_PRODUCTS_PATH + "/{productId}/sale-status", productId)
                .headers(TestAdmins.of(ADMIN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .exchange(),
            ADMIN_PRODUCTS_PATH + "/" + productId + "/sale-status");

        assertThat(storedStatus()).isEqualTo("DELISTED");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-15 등록 취소한 상품을 같은 값으로 수정해도 409이고, version이 그대로며 이벤트도 나가지 않는다")
    void rejectUpdateWithSameValues() {
        long version = storedVersion();
        Map<String, Object> body = productA(saved);
        body.put("version", version);
        body.put("options", null);
        body.put("prices", null);
        body.put("sizeSpecs", null);

        expectDelisted(
            client.put().uri(PRODUCTS_PATH + "/{productId}", productId)
                .headers(TestMembers.seller(SELLER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange(),
            PRODUCTS_PATH + "/" + productId);

        assertThat(storedVersion()).isEqualTo(version);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-15 등록 취소한 상품의 옵션 재고를 조정하면 409이고, 조정 기록이 남지 않는다")
    void rejectInventoryAdjustment() {
        long optionId = firstOptionId();

        expectDelisted(adjustInventory(TestMembers.seller(SELLER_ID), optionId), adjustmentsPath(optionId));

        assertThat(sellerAdjustmentCount(optionId)).isZero();
    }

    @Test
    @DisplayName("SALE-15 다른 판매자가 등록 취소 상품의 재고를 조정하면 409가 아니라 403이다")
    void rejectInventoryAdjustmentOfOtherSellerWith403First() {
        long optionId = firstOptionId();

        adjustInventory(TestMembers.seller(OTHER_SELLER_ID), optionId)
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("상품을 바꿀 권한이 없습니다");

        assertThat(sellerAdjustmentCount(optionId)).isZero();
    }

    private RestTestClient.ResponseSpec adjustInventory(Consumer<HttpHeaders> login, long optionId) {
        return client.post().uri(adjustmentsPath(optionId))
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("deltaQuantity", 10, "reason", "RESTOCK"))
            .exchange();
    }

    private static String adjustmentsPath(long optionId) {
        return "/api/v1/seller/options/" + optionId + "/inventory-adjustments";
    }

    private long firstOptionId() {
        return jdbcTemplate.queryForObject(
            "select min(id) from product.product_option where product_id = ?", Long.class, Long.valueOf(productId));
    }

    // 옵션을 등록할 때 남긴 INITIAL 기록을 뺀, 판매자가 조정한 기록 수다
    private int sellerAdjustmentCount(long optionId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.inventory_adjustment_record"
                + " where product_option_id = ? and reason <> 'INITIAL'",
            Integer.class, optionId);
    }

    private void expectDelisted(RestTestClient.ResponseSpec response, String instance) {
        response
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo(DELISTED_TITLE)
            .jsonPath("$.status").isEqualTo(HttpStatus.CONFLICT.value())
            .jsonPath("$.detail").isEqualTo(DELISTED_DETAIL)
            .jsonPath("$.instance").isEqualTo(instance);
    }

    private RestTestClient.ResponseSpec changeSaleStatusBySeller(String status) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", status))
            .exchange();
    }

    private long storedVersion() {
        return jdbcTemplate.queryForObject(
            "select version from product.product where id = ?", Long.class, Long.valueOf(productId));
    }

    private String storedStatus() {
        return jdbcTemplate.queryForObject(
            "select status from product.product where id = ?", String.class, Long.valueOf(productId));
    }
}
