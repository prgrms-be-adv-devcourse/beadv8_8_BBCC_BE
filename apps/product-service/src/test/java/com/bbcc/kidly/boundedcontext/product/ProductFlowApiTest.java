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

@FunctionalTest
class ProductFlowApiTest {

    private static final String SELLER_PRODUCTS_PATH = "/api/v1/seller/products";
    private static final String PRODUCTS_PATH = "/api/v1/products";

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

    private void approveSeller() {
        var approved = new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(SELLER_ID, BUSINESS_NAME, SellerStatus.ACTIVE));
        replicaSellerService.applySellerChanged(approved);
    }

    private String registerProductA() {
        Map<String, Object> body = productA(saved);
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
