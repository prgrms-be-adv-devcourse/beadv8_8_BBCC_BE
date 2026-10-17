package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.attribute;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.attributes;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.id;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.ownImageUrl;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.FeatureTopSaver.FeatureTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
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

    @Autowired
    private FeatureTopSaver featureTopSaver;

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
    @DisplayName("브랜드 · 설명 · 썸네일이 없으면 null이고 이미지가 없으면 빈 목록이다")
    void detailHasNullAndEmptyForMissingValues() {
        Map<String, Object> body = productA(saved);
        body.put("brand", null);
        body.put("description", null);
        ProductRegisterResponse registered = register(body);

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.brand").isEqualTo(null)
            .jsonPath("$.description").isEqualTo(null)
            .jsonPath("$.thumbnailUrl").isEqualTo(null)
            .jsonPath("$.imageUrls").isArray()
            .jsonPath("$.imageUrls.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("INFO-01 정보 항목은 항목마다 한 칸이고, 다중 선택 값은 valueIds에 모으고 직접 입력 값은 text에 담는다")
    void detailHasAttributesInRequestShape() {
        FeatureTop featureTop = featureTopSaver.save(saved);
        Map<String, Object> body = productA(saved);
        body.put("categoryId", id(featureTop.category().getId()));
        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("attributeId", id(featureTop.composition().getId()));
        composition.put("valueIds", List.of());
        composition.put("text", "면 95%, 스판 5%");
        attributes(body).add(attribute(featureTop.feature().getId(),
            List.of(id(featureTop.brushed().getId()), id(featureTop.stretch().getId()))));
        attributes(body).add(composition);
        ProductRegisterResponse registered = register(body);

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.attributes.length()").isEqualTo(3)
            .jsonPath("$.attributes[0].attributeId").isEqualTo(id(saved.clothing().mainMaterial().getId()))
            .jsonPath("$.attributes[0].valueIds").isEqualTo(List.of(id(saved.clothing().cotton().getId())))
            .jsonPath("$.attributes[0].text").isEqualTo(null)
            .jsonPath("$.attributes[1].attributeId").isEqualTo(id(featureTop.feature().getId()))
            .jsonPath("$.attributes[1].valueIds").isEqualTo(
                List.of(id(featureTop.brushed().getId()), id(featureTop.stretch().getId())))
            .jsonPath("$.attributes[1].text").isEqualTo(null)
            .jsonPath("$.attributes[2].attributeId").isEqualTo(id(featureTop.composition().getId()))
            .jsonPath("$.attributes[2].valueIds").isEqualTo(List.of())
            .jsonPath("$.attributes[2].text").isEqualTo("면 95%, 스판 5%");
    }

    @Test
    @DisplayName("상품을 고칠 때 다시 보낼 옵션 값 · 가격 구분 묶음 · 실측을 수정 요청과 같은 모양으로 돌려준다")
    void detailHasSaleInputsInRequestShape() {
        // 상품 A는 네이비 110 · 네이비 120 옵션이고, 의류는 가격 구분 항목이 없어 가격 구분 묶음은 빈 값 목록 하나(25,000원)다.
        // 실측은 110이 권장 키 105 ~ 115cm · 총장 42 · 어깨너비 30 · 가슴단면 34 · 소매길이 38, 120이 115 ~ 125cm · 45 · 32 · 36 · 41이다
        ClothingOptions clothing = saved.clothing();
        ProductRegisterResponse registered = register(productA(saved));

        detail(tokens.seller(SELLER_ID), registered.productId())
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options[0].valueIds").isEqualTo(
                List.of(id(clothing.navy().getId()), id(clothing.size110().getId())))
            .jsonPath("$.options[1].valueIds").isEqualTo(
                List.of(id(clothing.navy().getId()), id(clothing.size120().getId())))
            .jsonPath("$.prices.length()").isEqualTo(1)
            .jsonPath("$.prices[0].valueIds").isEqualTo(List.of())
            .jsonPath("$.prices[0].price").isEqualTo(25000)
            .jsonPath("$.sizeSpecs.length()").isEqualTo(2)
            .jsonPath("$.sizeSpecs[0].sizeValueId").isEqualTo(id(clothing.size110().getId()))
            .jsonPath("$.sizeSpecs[0].minHeightCm").isEqualTo(105)
            .jsonPath("$.sizeSpecs[0].maxHeightCm").isEqualTo(115)
            .jsonPath("$.sizeSpecs[0].measurements.TOTAL_LENGTH").value(cm -> assertCm(cm, "42"))
            .jsonPath("$.sizeSpecs[0].measurements.SHOULDER_WIDTH").value(cm -> assertCm(cm, "30"))
            .jsonPath("$.sizeSpecs[0].measurements.CHEST_WIDTH").value(cm -> assertCm(cm, "34"))
            .jsonPath("$.sizeSpecs[0].measurements.SLEEVE_LENGTH").value(cm -> assertCm(cm, "38"))
            .jsonPath("$.sizeSpecs[1].sizeValueId").isEqualTo(id(clothing.size120().getId()))
            .jsonPath("$.sizeSpecs[1].minHeightCm").isEqualTo(115)
            .jsonPath("$.sizeSpecs[1].maxHeightCm").isEqualTo(125)
            .jsonPath("$.sizeSpecs[1].measurements.TOTAL_LENGTH").value(cm -> assertCm(cm, "45"));
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

    // 실측 값은 소수점 표기와 상관없이 같은 cm인지 본다
    private static void assertCm(Object actual, String expectedCm) {
        assertThat(new BigDecimal(String.valueOf(actual))).isEqualByComparingTo(expectedCm);
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
