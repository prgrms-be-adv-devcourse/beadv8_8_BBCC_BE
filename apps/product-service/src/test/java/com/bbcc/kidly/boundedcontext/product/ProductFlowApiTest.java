package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 등록한 상품이 구매자의 상품 상세까지 이어지는지 HTTP 요청만으로 확인한다.
 * 판매자 승인은 회원 서비스에 있어 그때 나가는 판매자 정보 변경됨만 테스트가 발행하고, 상품이 받아 판매자 복사본을 만든다.
 * 구매자 상품 목록(GET /api/v1/products)은 검색 서비스(search-service)에 있어 이 테스트에서는 보지 않는다(#791).
 * 추천은 이 이벤트들을 Kafka로 받는 별도 서비스(recommend-service)라 이 테스트에서는 보지 않는다.
 * 상품 유형과 카테고리는 API가 없는 기준 데이터라서 매 테스트 전에 저장한다.
 */
@FunctionalTest
class ProductFlowApiTest {

    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String PRODUCTS_PATH = "/api/v1/products";

    // 판매자 ID는 회원 ID와 같다
    private static final long SELLER_ID = 7L;
    private static final String BUSINESS_NAME = "키들리 상점";

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private ProductReplicaSellerService replicaSellerService;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("판매자가 등록한 상품은 판매를 시작해야 구매자 상세에 나온다")
    void registeredProductReachesBuyer() {
        approveSeller();
        String productId = registerProductA();

        expectHiddenBeforeSale(productId);

        startSale(productId);

        expectBuyerDetail(productId);
    }

    // 회원 서비스가 회원 7의 판매자 신청을 승인할 때 보내는 판매자 정보 변경됨이다. 상품이 받아 상호명을 저장한다
    private void approveSeller() {
        var approved = new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(SELLER_ID, BUSINESS_NAME, SellerStatus.ACTIVE));
        replicaSellerService.applySellerChanged(approved);
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

    // 판매를 시작하기 전 상품은 구매자 상세에서 보이지 않는다
    private void expectHiddenBeforeSale(String productId) {
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
}
