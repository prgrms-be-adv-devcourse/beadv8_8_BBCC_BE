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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 기존 판매 상태 변경 주소에 STOPPED를 보내 판매를 중지하거나, ON_SALE로 다시 여는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 등록 API로 만들고 판매 시작 요청으로 판매중으로 바꾼다. 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class ProductSaleStopApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;
    private static final long OTHER_SELLER_ID = 8L;
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
    @DisplayName("SALE-08 판매중 상품에 STOPPED를 보내면 판매중지가 되고, 커밋 뒤 상품 정보 변경됨이 한 번 나간다")
    void stopSaleOfOnSaleProduct() {
        startSale();

        changeSaleStatus(tokens.seller(SELLER_ID), STOPPED)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("STOPPED");
        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().status()).isEqualTo(ProductStatus.STOPPED));
    }

    @Test
    @DisplayName("이미 판매중지인 상품에 STOPPED를 다시 보내면 200이고, version이 그대로이며 이벤트도 나가지 않는다")
    void stopSaleAgainChangesNothing() {
        startSale();
        changeSaleStatus(tokens.seller(SELLER_ID), STOPPED).expectStatus().isOk();
        long versionAfterFirst = storedVersion();
        eventRecorder.clear();

        changeSaleStatus(tokens.seller(SELLER_ID), STOPPED)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.version").isEqualTo(versionAfterFirst);

        assertThat(storedVersion()).isEqualTo(versionAfterFirst);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("판매대기 상품에 STOPPED를 보내면 409이고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectStopSaleOfPendingProduct() {
        expectProblem(changeSaleStatus(tokens.seller(SELLER_ID), STOPPED), HttpStatus.CONFLICT,
            "판매를 시작하지 않은 상품입니다", "판매를 시작한 상품만 판매를 중지할 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-03 · SALE-08 판매자가 판매중지한 상품에 ON_SALE을 보내면 판매가 다시 열리고 상품 정보 변경됨이 한 번 나간다")
    void restartSaleOfStoppedProduct() {
        startSale();
        changeSaleStatus(tokens.seller(SELLER_ID), STOPPED).expectStatus().isOk();
        eventRecorder.clear();

        changeSaleStatus(tokens.seller(SELLER_ID), ON_SALE)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ON_SALE");

        assertThat(storedStatus()).isEqualTo("ON_SALE");
        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().status()).isEqualTo(ProductStatus.ON_SALE));
    }

    @Test
    @DisplayName("SALE-11 관리자가 판매중지한 상품에 판매자가 ON_SALE을 보내면 409이고, 판매중지로 남고 이벤트도 나가지 않는다")
    void rejectRestartSaleOfAdminStoppedProduct() {
        startSale();
        // 관리자 판매중지 API는 따로 확인하므로, 여기서는 관리자가 판매중지한 상태를 표로 만든다
        jdbcTemplate.update("update product.product set status = 'STOPPED', is_stopped_by_admin = true where id = ?",
            Long.valueOf(productId));

        expectProblem(changeSaleStatus(tokens.seller(SELLER_ID), ON_SALE), HttpStatus.CONFLICT,
            "관리자가 판매중지한 상품입니다",
            "관리자가 판매중지한 상품은 판매자가 다시 판매를 시작할 수 없습니다. 관리자에게 문의해 주세요.");
        assertThat(storedStatus()).isEqualTo("STOPPED");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("OPT-08 다른 판매자의 상품에 STOPPED를 보내면 403이고, 판매중으로 남고 이벤트도 나가지 않는다")
    void rejectStopSaleOfOtherSellersProduct() {
        startSale();

        expectProblem(changeSaleStatus(tokens.seller(OTHER_SELLER_ID), STOPPED), HttpStatus.FORBIDDEN,
            "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("ON_SALE");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    // 실패 응답이 ProblemDetail 5필드(type, title, status, detail, instance)인지 본다
    private void expectProblem(RestTestClient.ResponseSpec response, HttpStatus status, String title, String detail) {
        response
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PRODUCTS_PATH + "/" + productId + "/sale-status");
    }

    // 판매 시작 요청으로 판매중으로 바꾸고, 그때 나간 이벤트는 지운다
    private void startSale() {
        changeSaleStatus(tokens.seller(SELLER_ID), ON_SALE).expectStatus().isOk();
        eventRecorder.clear();
    }

    private RestTestClient.ResponseSpec changeSaleStatus(String token, Map<String, Object> body) {
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
