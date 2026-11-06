package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestMembers;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 상품을 더 팔지 않겠다고 등록 취소하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 등록 API로 만들고 판매 상태 변경 요청으로 원하는 상태로 만든다. 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class ProductDelistApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;
    private static final Map<String, Object> ON_SALE = Map.of("status", "ON_SALE");
    private static final Map<String, Object> STOPPED = Map.of("status", "STOPPED");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String productId;

    @BeforeEach
    void registerProductA() {
        SavedClothingTop saved = clothingTopSaver.save();
        productId = client.post().uri(PRODUCTS_PATH)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(productA(saved))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
        eventRecorder.clear();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"판매대기", "판매중", "판매자가 판매중지한 상품", "관리자가 판매중지한 상품"})
    @DisplayName("SALE-13 판매자가 어느 상태의 상품이든 등록 취소하면 200이고 상태가 DELISTED가 된다")
    void delistInAnyState(String given) {
        prepare(given);

        delist(TestMembers.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("DELISTED")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("DELISTED");
    }

    @Test
    @DisplayName("SALE-13 이미 등록 취소한 상품을 다시 등록 취소하면 200이고, 상태와 version이 그대로다")
    void delistAgainChangesNothing() {
        delist(TestMembers.seller(SELLER_ID)).expectStatus().isOk();
        long versionAfterFirst = storedVersion();

        delist(TestMembers.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DELISTED")
            .jsonPath("$.version").isEqualTo(versionAfterFirst);

        assertThat(storedVersion()).isEqualTo(versionAfterFirst);
    }

    // 판매 상태 변경 API로 상품을 원하는 상태까지 옮긴다. 관리자 판매중지 API는 따로 확인하므로 그 상태는 표로 만든다
    private void prepare(String given) {
        if (given.equals("판매대기")) {
            return;
        }
        changeSaleStatus(ON_SALE);
        if (given.equals("판매자가 판매중지한 상품")) {
            changeSaleStatus(STOPPED);
        }
        if (given.equals("관리자가 판매중지한 상품")) {
            jdbcTemplate.update(
                "update product.product set status = 'STOPPED', is_stopped_by_admin = true where id = ?",
                Long.valueOf(productId));
        }
        eventRecorder.clear();
    }

    private void changeSaleStatus(Map<String, Object> body) {
        client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec delist(Consumer<HttpHeaders> login) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}/delisting", productId)
            .headers(login)
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
