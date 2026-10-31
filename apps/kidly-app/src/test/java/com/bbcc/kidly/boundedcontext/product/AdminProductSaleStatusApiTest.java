package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    @DisplayName("SALE-11 · SALE-08 관리자가 판매중 상품에 STOPPED를 보내면 판매중지와 관리자 판매중지 표시가 되고, 상품 정보 변경됨이 한 번 나간다")
    void stopSaleByAdmin() {
        startSaleBySeller();

        changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), STOPPED)
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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"판매대기", "판매자가 판매중지한 상품", "관리자가 판매중지한 상품"})
    @DisplayName("SALE-03 · SALE-11 관리자가 ON_SALE을 보내면 판매 시작 조건 검사 뒤 판매중이 되고, 관리자 판매중지 표시가 꺼지며 이벤트가 한 번 나간다")
    void startSaleByAdmin(String given) {
        if (!given.equals("판매대기")) {
            startSaleBySeller();
            if (given.equals("판매자가 판매중지한 상품")) {
                changeSaleStatusBySeller(STOPPED);
            } else {
                changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), STOPPED).expectStatus().isOk();
            }
            eventRecorder.clear();
        }

        changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), ON_SALE)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ON_SALE")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("ON_SALE");
        assertThat(storedStoppedByAdmin()).isFalse();
        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().status()).isEqualTo(ProductStatus.ON_SALE));
    }

    @Test
    @DisplayName("SALE-11 판매자가 판매중지한 상품에 관리자가 STOPPED를 보내면 표시만 켜지고, 응답 version이 DB와 같으며 이벤트는 나가지 않는다")
    void stopSaleByAdminOfSellerStoppedProduct() {
        startSaleBySeller();
        changeSaleStatusBySeller(STOPPED);
        eventRecorder.clear();

        changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), STOPPED)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("STOPPED")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStoppedByAdmin()).isTrue();
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("관리자도 판매대기 상품에 STOPPED를 보내면 409이고 이벤트가 나가지 않는다")
    void rejectStopSaleByAdminOfPendingProduct() {
        expectProblem(changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), STOPPED), HttpStatus.CONFLICT,
            "판매를 시작하지 않은 상품입니다", "판매를 시작한 상품만 판매를 중지할 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-03 관리자도 판매 시작 조건을 채우지 못한 상품에 ON_SALE을 보내면 409이고 이벤트가 나가지 않는다")
    void rejectStartSaleByAdminWithoutSaleStartConditions() {
        // 등록할 때 같은 검사를 하므로, 등록한 뒤 120 사이즈의 실측을 지워 조건을 못 채우게 한다
        jdbcTemplate.update("delete from product.size_spec where product_id = ? and attribute_value_id = "
            + "(select id from product.attribute_value where name = '120')", Long.valueOf(productId));

        expectProblem(changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), ON_SALE), HttpStatus.CONFLICT,
            "판매를 시작할 수 없습니다", "판매를 시작할 수 없습니다. 120 사이즈에 실측과 권장 키가 없습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("없는 상품이면 404, 정해진 값이 아닌 판매 상태면 400이다")
    void rejectMissingProductAndInvalidStatus() {
        expectProblem(changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), Map.of("status", "FOO")),
            HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "status: 판매 시작(ON_SALE)이나 판매 중지(STOPPED)만 요청할 수 있습니다.");
        productId = "999999";
        expectProblem(changeSaleStatusByAdmin(TestMembers.admin(ADMIN_ID), STOPPED), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("토큰이 없으면 401, 관리자가 아닌 회원(판매자)이면 403이다")
    void rejectWithoutAdminRole() {
        startSaleBySeller();

        changeSaleStatusByAdmin(TestMembers.anonymous(), STOPPED).expectStatus().isUnauthorized();
        changeSaleStatusByAdmin(TestMembers.seller(SELLER_ID), STOPPED).expectStatus().isForbidden();
        assertThat(storedStatus()).isEqualTo("ON_SALE");
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
            .jsonPath("$.instance").isEqualTo(ADMIN_PRODUCTS_PATH + "/" + productId + "/sale-status");
    }

    private void changeSaleStatusBySeller(Map<String, Object> body) {
        client.put().uri(SELLER_PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk();
    }

    // 판매자의 판매 시작 요청으로 판매중으로 바꾸고, 그때 나간 이벤트는 지운다
    private void startSaleBySeller() {
        changeSaleStatusBySeller(ON_SALE);
        eventRecorder.clear();
    }

    private RestTestClient.ResponseSpec changeSaleStatusByAdmin(
        Consumer<HttpHeaders> login, Map<String, Object> body) {
        return client.put().uri(ADMIN_PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(login)
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
