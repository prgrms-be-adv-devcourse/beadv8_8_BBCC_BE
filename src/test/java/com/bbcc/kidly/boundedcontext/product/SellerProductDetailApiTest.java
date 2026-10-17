package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.id;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.ownImageUrl;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
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
 * 판매자 내 상품 상세 API를 HTTP 요청부터 응답까지 확인한다. 상품은 등록 API로 만든다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class SellerProductDetailApiTest {

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
    private JdbcTemplate jdbcTemplate;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("판매대기 상품도 상품 정보와 옵션별 옵션 이름 · 판매가 · 판매 가능 수량 · 판매 상태 · SKU 코드를 돌려준다")
    void detailOfPendingProduct() {
        // 상품 A는 네이비 110(초기 재고 10, SKU A-NV-110) · 네이비 120(초기 재고 0, SKU 없음) 옵션이고 판매가는 25,000원이다
        ProductRegisterResponse registered = register(productA(saved));

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(registered.productId())
            .jsonPath("$.name").isEqualTo("상품 A")
            .jsonPath("$.status").isEqualTo("SALE_PENDING")
            .jsonPath("$.version").isEqualTo(registered.version())
            .jsonPath("$.options.length()").isEqualTo(2)
            .jsonPath("$.options[0].optionId").isEqualTo(registered.options().get(0).optionId())
            .jsonPath("$.options[0].optionName").isEqualTo("색상: 네이비 / 사이즈: 110")
            .jsonPath("$.options[0].price").isEqualTo(25000)
            .jsonPath("$.options[0].availableQuantity").isEqualTo(10)
            .jsonPath("$.options[0].saleStatus").isEqualTo("ON_SALE")
            .jsonPath("$.options[0].skuCode").isEqualTo("A-NV-110")
            .jsonPath("$.options[1].optionId").isEqualTo(registered.options().get(1).optionId())
            .jsonPath("$.options[1].optionName").isEqualTo("색상: 네이비 / 사이즈: 120")
            .jsonPath("$.options[1].availableQuantity").isEqualTo(0)
            .jsonPath("$.options[1].skuCode").isEqualTo(null);
    }

    @Test
    @DisplayName("상품을 고칠 때 다시 보낼 기본 정보와 이미지를 수정 요청과 같은 모양으로 돌려준다")
    void detailHasBasicInfoAndImages() {
        String thumbnail = ownImageUrl(SELLER_ID);
        String secondImage = thumbnail.replace("0b8c1e2a", "1a2b3c4d");
        Map<String, Object> body = productA(saved);
        body.put("thumbnailUrl", thumbnail);
        body.put("imageUrls", List.of(secondImage, thumbnail));
        ProductRegisterResponse registered = register(body);

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.brand").isEqualTo("키들리")
            .jsonPath("$.categoryId").isEqualTo(String.valueOf(saved.top().getId()))
            .jsonPath("$.description").isEqualTo("상품 설명")
            .jsonPath("$.thumbnailUrl").isEqualTo(thumbnail)
            .jsonPath("$.imageUrls.length()").isEqualTo(2)
            .jsonPath("$.imageUrls[0]").isEqualTo(secondImage)
            .jsonPath("$.imageUrls[1]").isEqualTo(thumbnail);
    }

    @Test
    @DisplayName("SALE-06 보관 옵션은 상세에 보이지 않고, 판매중지 옵션은 STOPPED로 보인다")
    void detailHidesArchivedOptions() {
        // 상품 A에 오트밀 110 옵션을 더해 옵션 세 개로 등록하고, 네이비 120은 판매중지, 오트밀 110은 보관으로 바꾼다
        ClothingOptions clothing = saved.clothing();
        Map<String, Object> body = productA(saved);
        options(body).add(
            option(List.of(id(clothing.oatmeal().getId()), id(clothing.size110().getId())), 5, null));
        ProductRegisterResponse registered = register(body);
        changeSaleStatus(registered.options().get(1).optionId(), "STOPPED");
        changeSaleStatus(registered.options().get(2).optionId(), "ARCHIVED");

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options.length()").isEqualTo(2)
            .jsonPath("$.options[0].optionId").isEqualTo(registered.options().get(0).optionId())
            .jsonPath("$.options[0].saleStatus").isEqualTo("ON_SALE")
            .jsonPath("$.options[1].optionId").isEqualTo(registered.options().get(1).optionId())
            .jsonPath("$.options[1].saleStatus").isEqualTo("STOPPED");
    }

    @Test
    @DisplayName("다른 판매자의 상품이면 403이다")
    void rejectOtherSellersProduct() {
        ProductRegisterResponse registered = register(productA(saved));

        expectProblem(detail(tokens.seller(OTHER_SELLER_ID), registered.productId()), registered.productId(),
            HttpStatus.FORBIDDEN, "상품을 볼 권한이 없습니다", "본인이 등록한 상품만 볼 수 있습니다.");
    }

    @Test
    @DisplayName("없는 상품이면 404다")
    void rejectMissingProduct() {
        expectProblem(detail(tokens.seller(SELLER_ID), "999999"), "999999",
            HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("상품 ID가 숫자가 아니면 400이다")
    void rejectNonNumericProductId() {
        detail(tokens.seller(SELLER_ID), "abc")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("토큰이 없으면 401, 판매자가 아닌 회원이면 403이다")
    void rejectWithoutSellerRole() {
        ProductRegisterResponse registered = register(productA(saved));

        detail(null, registered.productId()).expectStatus().isUnauthorized();
        detail(tokens.buyer(SELLER_ID), registered.productId()).expectStatus().isForbidden();
    }

    // 등록 API로 판매자 7의 판매대기 상품을 만든다
    private ProductRegisterResponse register(Map<String, Object> body) {
        return client.post().uri(PRODUCTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody();
    }

    // 옵션의 판매 상태를 바꾸는 업무 메서드가 아직 없어 표를 직접 고친다
    private void changeSaleStatus(String optionId, String saleStatus) {
        jdbcTemplate.update("update product.product_option set sale_status = ? where id = ?",
            saleStatus, Long.valueOf(optionId));
    }

    // 실패 응답이 ProblemDetail 5필드(type, title, status, detail, instance)인지 본다
    private void expectProblem(
        RestTestClient.ResponseSpec response, String productId, HttpStatus status, String title, String detail) {
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

    private RestTestClient.ResponseSpec detail(String token, String productId) {
        return client.get().uri(PRODUCTS_PATH + "/{productId}", productId)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .exchange();
    }
}
