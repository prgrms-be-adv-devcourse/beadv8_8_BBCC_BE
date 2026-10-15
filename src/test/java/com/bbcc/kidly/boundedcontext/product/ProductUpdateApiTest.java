package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
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
 * 판매자가 상품을 고치는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품 A는 등록 API로 만든 판매대기 상의이고 옵션 네이비 · 110(초기 재고 10, SKU A-NV-110)과 네이비 · 120(초기 재고 0)이 있다.
 * 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class ProductUpdateApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;
    private static final long OTHER_SELLER_ID = 8L;

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

    private String productId;

    @BeforeEach
    void registerProductA() {
        saved = clothingTopSaver.save();
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
    @DisplayName("SALE-08 판매를 시작한 상품의 기본 정보를 고치면 200과 새 version을 담은 내 상품 상세를 받고, "
        + "커밋 뒤 상품 정보 변경됨이 한 번 나간다")
    void updateOnSaleProduct() {
        startSale();
        long before = storedVersion();
        Map<String, Object> body = updateBody(before);
        body.put("name", "상품 A 새 이름");

        update(tokens.seller(SELLER_ID), body)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.name").isEqualTo("상품 A 새 이름")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedVersion()).isGreaterThan(before);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .extracting(event -> event.data().name()).isEqualTo("상품 A 새 이름");
    }

    @Test
    @DisplayName("같은 수정 요청을 다시 보내면 200을 받고, version은 그대로이며 상품 정보 변경됨도 다시 나가지 않는다")
    void retrySameUpdate() {
        startSale();
        Map<String, Object> body = updateBody(storedVersion());
        body.put("name", "상품 A 새 이름");
        update(tokens.seller(SELLER_ID), body).expectStatus().isOk();
        long afterFirst = storedVersion();
        eventRecorder.clear();

        update(tokens.seller(SELLER_ID), body)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.version").isEqualTo(afterFirst);

        assertThat(storedVersion()).isEqualTo(afterFirst);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품을 불러온 뒤 다른 곳에서 먼저 고쳤으면, 예전 version으로 다른 값을 보낼 때 409 ProblemDetail을 받고 먼저 고친 값은 그대로다")
    void rejectStaleVersion() {
        long version = storedVersion();
        Map<String, Object> first = updateBody(version);
        first.put("name", "먼저 고친 이름");
        update(tokens.seller(SELLER_ID), first).expectStatus().isOk();
        Map<String, Object> late = updateBody(version);
        late.put("brand", "늦게 고친 브랜드");

        expectProblem(update(tokens.seller(SELLER_ID), late), HttpStatus.CONFLICT,
            "다른 곳에서 먼저 바뀐 상품입니다", "상품을 다시 불러온 뒤 고쳐 주세요.");
        assertThat(jdbcTemplate.queryForObject("select name from product.product where id = ?", String.class,
            Long.valueOf(productId))).isEqualTo("먼저 고친 이름");
    }

    @Test
    @DisplayName("OPT-08 등록한 판매자만 상품을 고칠 수 있으므로, 다른 판매자가 고치면 403 ProblemDetail을 받는다")
    void rejectOtherSeller() {
        expectProblem(update(tokens.seller(OTHER_SELLER_ID), updateBody(storedVersion())), HttpStatus.FORBIDDEN,
            "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다.");
    }

    @Test
    @DisplayName("없는 상품을 고치면 404를 받는다")
    void rejectMissingProduct() {
        client.put().uri(PRODUCTS_PATH + "/{productId}", 999_999L)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(updateBody(storedVersion()))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("로그인하지 않고 고치면 401을 받는다")
    void rejectWithoutLogin() {
        update(null, updateBody(storedVersion())).expectStatus().isUnauthorized();
    }

    // 상품 A를 등록할 때와 같은 값에 version을 더한 수정 요청이다. 옵션 · 가격 · 실측은 보내지 않는다
    private Map<String, Object> updateBody(long version) {
        Map<String, Object> body = productA(saved);
        body.put("version", version);
        body.put("options", null);
        body.put("prices", null);
        body.put("sizeSpecs", null);
        return body;
    }

    private RestTestClient.ResponseSpec update(String token, Map<String, Object> body) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}", productId)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
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
            .jsonPath("$.instance").isEqualTo(PRODUCTS_PATH + "/" + productId);
    }

    private void startSale() {
        client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", "ON_SALE"))
            .exchange()
            .expectStatus().isOk();
        eventRecorder.clear();
    }

    private long storedVersion() {
        return jdbcTemplate.queryForObject(
            "select version from product.product where id = ?", Long.class, Long.valueOf(productId));
    }
}
