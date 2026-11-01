package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 내 상품 상세 응답을 그대로 상품 수정 요청에 넣어 보낼 수 있는지 HTTP 요청부터 응답까지 확인한다.
 * 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class SellerProductDetailRoundTripApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;

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
    @DisplayName("판매대기 상품은 상세를 그대로 옮긴 수정 요청이 200이고, 바뀐 것이 없어 수정 응답이 상세와 같다")
    void resubmitPendingProductDetail() {
        ProductRegisterResponse registered = register(productA(saved));
        Map<String, Object> before = detail(registered.productId());

        Map<String, Object> after = update(registered.productId(), updateBodyFrom(before));

        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("판매중 상품은 상세를 옮기고 옵션 · 가격 · 실측만 null로 보낸 수정 요청이 200이고, 수정 응답이 상세와 같다")
    void resubmitOnSaleProductDetailWithoutSaleInputs() {
        ProductRegisterResponse registered = register(productA(saved));
        startSale(registered.productId());
        Map<String, Object> before = detail(registered.productId());
        Map<String, Object> body = updateBodyFrom(before);
        // 옵션 · 가격 · 실측은 판매대기 상품에서만 바꿀 수 있어 판매중 상품은 셋 다 보내지 않는다
        body.put("options", null);
        body.put("prices", null);
        body.put("sizeSpecs", null);

        Map<String, Object> after = update(registered.productId(), body);

        assertThat(after).isEqualTo(before);
    }

    // 상세 응답에서 수정 요청이 받는 칸만 옮긴다. 옵션의 초기 재고는 판매 가능 수량으로 넣는다
    private static Map<String, Object> updateBodyFrom(Map<String, Object> detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : List.of("version", "name", "brand", "categoryId", "description", "thumbnailUrl",
            "imageUrls", "attributes", "prices", "sizeSpecs")) {
            body.put(key, detail.get(key));
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> option : listOfMaps(detail.get("options"))) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("valueIds", option.get("valueIds"));
            input.put("initialQuantity", option.get("availableQuantity"));
            input.put("skuCode", option.get("skuCode"));
            options.add(input);
        }
        body.put("options", options);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    // 등록 API로 판매자 7의 판매대기 상품을 만든다
    private ProductRegisterResponse register(Map<String, Object> body) {
        return client.post().uri(PRODUCTS_PATH)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody();
    }

    private void startSale(String productId) {
        client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", "ON_SALE"))
            .exchange()
            .expectStatus().isOk();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detail(String productId) {
        return client.get().uri(PRODUCTS_PATH + "/{productId}", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isOk()
            .returnResult(Map.class)
            .getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> update(String productId, Map<String, Object> body) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}", productId)
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
            .returnResult(Map.class)
            .getResponseBody();
    }
}
