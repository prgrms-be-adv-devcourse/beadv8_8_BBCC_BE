package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 등록 취소한 상품을 판매중지로 되돌리는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 판매자 등록 API로 만들고 판매자 등록 취소 API로 등록 취소 상태로 만든다. 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class AdminProductDelistRevertApiTest {

    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String ADMIN_PRODUCTS_PATH = "/api/v1/admin/products";
    private static final long SELLER_ID = 7L;
    private static final long ADMIN_ID = 1L;

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
        productId = client.post().uri(SELLER_PRODUCTS_PATH)
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

    @Test
    @DisplayName("SALE-14 관리자가 등록 취소한 상품을 되돌리면 200이고 상태가 STOPPED가 된다")
    void revertDelisting() {
        delistBySeller();

        revert(TestAdmins.of(ADMIN_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("STOPPED");
    }

    @Test
    @DisplayName("SALE-08 · SALE-17 되돌리면 커밋 뒤 상품 정보 변경됨이 한 번 나가고 status가 STOPPED다")
    void revertPublishesProductChanged() {
        delistBySeller();

        revert(TestAdmins.of(ADMIN_ID)).expectStatus().isOk();

        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.data().productId()).isEqualTo(Long.valueOf(productId));
                assertThat(event.data().status()).isEqualTo(ProductStatus.STOPPED);
            });
    }

    // 판매자 등록 취소 API로 상품을 등록 취소 상태로 만들고, 그때 나간 이벤트는 지운다
    private void delistBySeller() {
        client.put().uri(SELLER_PRODUCTS_PATH + "/{productId}/delisting", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isOk();
        eventRecorder.clear();
    }

    private RestTestClient.ResponseSpec revert(Consumer<HttpHeaders> login) {
        return client.delete().uri(ADMIN_PRODUCTS_PATH + "/{productId}/delisting", productId)
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
