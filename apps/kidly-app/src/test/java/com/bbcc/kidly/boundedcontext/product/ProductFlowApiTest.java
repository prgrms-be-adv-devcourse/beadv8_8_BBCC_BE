package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 등록한 상품이 구매자의 상품 목록 · 상세까지 이어지는지 HTTP 요청만으로 확인한다.
 * 판매자 정보 변경됨 · 상품 정보 변경됨은 테스트가 발행하지 않는다. 판매자 승인 · 판매 시작 API가 발행하고,
 * 상품의 리스너가 실제로 받아 판매자 복사본 · 검색용 표를 만든다.
 * 추천은 이 이벤트들을 Kafka로 받으므로(Kafka가 없는 기능 테스트에서는 받지 않는다) 추천까지 이어지는지는
 * RecommendKafkaFromProducersIntegrationTest가 확인한다.
 * 상품 유형과 카테고리는 API가 없는 기준 데이터라서 매 테스트 전에 저장한다.
 */
@FunctionalTest
class ProductFlowApiTest {

    private static final String APPLY_PATH = "/api/v1/members/me/seller-application";
    private static final String APPROVE_PATH = "/api/v1/admin/seller-applications/{applicationId}/approve";
    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String PRODUCTS_PATH = "/api/v1/products";

    // 판매자 ID는 회원 ID와 같다
    private static final long SELLER_ID = 7L;
    private static final long ADMIN_ID = 99L;
    private static final String BUSINESS_NAME = "키들리 상점";

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("판매자가 등록한 상품은 판매를 시작해야 구매자 목록 · 상세에 나온다")
    void registeredProductReachesBuyer() {
        approveSeller();
        String productId = registerProductA();

        expectHiddenBeforeSale(productId);

        startSale(productId);

        expectInBuyerList(productId);
        expectBuyerDetail(productId);
    }

    // 회원 7이 판매자 신청을 하고 관리자가 승인한다. 승인이 보낸 판매자 정보 변경됨으로 상품이 상호명을 받는다
    private void approveSeller() {
        AtomicReference<Object> applicationId = new AtomicReference<>();
        client.post().uri(APPLY_PATH)
            .headers(TestMembers.buyer(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("businessName", BUSINESS_NAME, "representativeName", "김판매",
                "businessRegistrationNumber", "1234567890"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.id").value(applicationId::set);

        client.post().uri(APPROVE_PATH, applicationId.get())
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED");
    }

    // 판매자 7이 상품 A를 등록한다. 네이비 110(105 ~ 115cm) · 네이비 120(115 ~ 125cm)이고 둘 다 재고 10, 25,000원이다
    private String registerProductA() {
        Map<String, Object> body = productA(saved);
        // 상세에서 두 옵션이 모두 구매 가능으로 보이도록 120에도 재고를 넣는다
        options(body).get(1).put("initialQuantity", 10);
        AtomicReference<String> productId = new AtomicReference<>();
        client.post().uri(SELLER_PRODUCTS_PATH)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.status").isEqualTo("SALE_PENDING")
            .jsonPath("$.productId").value(String.class, productId::set);
        return productId.get();
    }

    // 판매를 시작하기 전에는 상품 정보 변경됨이 나가지 않아서, 검색용 표에 상품이 없다
    private void expectHiddenBeforeSale(String productId) {
        client.get().uri(PRODUCTS_PATH)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0);
        client.get().uri(PRODUCTS_PATH + "/{productId}", productId)
            .exchange()
            .expectStatus().isNotFound();
    }

    private void startSale(String productId) {
        client.put().uri(SELLER_PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", "ON_SALE"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ON_SALE");
    }

    // 상호명은 판매자 복사본에서, 나머지는 검색용 표에서 온다.
    // 카테고리 · 옵션 값 · 상품 정보 값으로 걸러도 나오는지 확인해, 이벤트의 값 ID가 검색용 표까지 이어지는지 본다
    private void expectInBuyerList(String productId) {
        client.get().uri(PRODUCTS_PATH)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            // jsonPath의 isEqualTo는 숫자도 문자열로 바꿔 비교하므로 Map으로 꺼내 값의 타입까지 확인한다
            .jsonPath("$.content[0]").value(Map.class, card -> assertThat(card)
                .hasSize(7)
                .containsEntry("productId", productId)
                .containsEntry("name", "상품 A")
                .containsEntry("thumbnailUrl", null)
                .containsEntry("businessName", BUSINESS_NAME)
                .containsEntry("minPrice", 25_000)
                .containsEntry("soldOut", false)
                .containsEntry("sizeFree", false));

        ClothingOptions clothing = saved.clothing();
        client.get().uri(PRODUCTS_PATH + "?categoryId={categoryId}&attributes={color}&attributes={size}"
                + "&attributes={material}", saved.top().getId(), filter(clothing.navy()), filter(clothing.size110()),
                filter(clothing.cotton()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of(productId));
    }

    // 상세는 상품의 원본 표에서 읽는다. 재고 10은 기준 수 5보다 많아서 두 옵션 모두 수량 없이 구매 가능으로 보인다
    private void expectBuyerDetail(String productId) {
        client.get().uri(PRODUCTS_PATH + "/{productId}", productId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.name").isEqualTo("상품 A")
            .jsonPath("$.status").isEqualTo("ON_SALE")
            .jsonPath("$.minPrice").isEqualTo(25_000)
            .jsonPath("$.options[*].optionName").isEqualTo(List.of("색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120"))
            .jsonPath("$.options[*].purchasable").isEqualTo(List.of(true, true))
            .jsonPath("$.options[*].stockStatus").isEqualTo(List.of("AVAILABLE", "AVAILABLE"));
    }

    // 상품 목록 API가 받는 항목 값 형식 "항목code:값ID"다
    private static String filter(AttributeValue value) {
        return value.getAttribute().getCode() + ":" + value.getId();
    }
}
