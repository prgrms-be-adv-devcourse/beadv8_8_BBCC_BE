package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자가 상품 A를 등록하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품 유형과 카테고리는 매 테스트 전에 저장하고, 요청의 ID는 저장한 엔티티에서 꺼낸다.
 */
@FunctionalTest
class ProductRegistrationApiTest {

    private static final String PATH = "/api/v1/seller/products";

    private static final long SELLER_ID = 7L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    private SavedClothingTop saved;

    @BeforeEach
    void saveClothingTop() {
        saved = clothingTopSaver.save();
    }

    @Test
    @DisplayName("상품을 등록하면 201과 Location을 받고, 판매대기 상품의 옵션을 요청 순서대로 돌려받는다")
    void registerProduct() {
        AtomicReference<String> productId = new AtomicReference<>();

        URI location = client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .body(productA())
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.productId").value(String.class, productId::set)
            .jsonPath("$.status").isEqualTo("SALE_PENDING")
            .jsonPath("$.version").isEqualTo(0)
            .jsonPath("$.options.length()").isEqualTo(2)
            .jsonPath("$.options[0].optionId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$.options[0].optionName").isEqualTo("색상: 네이비 / 사이즈: 110")
            .jsonPath("$.options[0].price").isEqualTo(25000)
            .jsonPath("$.options[0].availableQuantity").isEqualTo(10)
            .jsonPath("$.options[0].saleStatus").isEqualTo("ON_SALE")
            .jsonPath("$.options[0].skuCode").isEqualTo("A-NV-110")
            .jsonPath("$.options[1].optionName").isEqualTo("색상: 네이비 / 사이즈: 120")
            .jsonPath("$.options[1].availableQuantity").isEqualTo(0)
            .jsonPath("$.options[1].skuCode").isEqualTo(null)
            .returnResult()
            .getResponseHeaders().getLocation();

        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo(PATH + "/" + productId.get());
    }

    /**
     * 기획서 7.1 상품 A의 등록 요청이다. 네이비 110(초기 재고 10, SKU 있음) · 네이비 120(초기 재고 0, SKU 없음) 옵션과
     * 두 사이즈의 실측 · 권장 키를 담는다. 테스트마다 고칠 수 있게 바꿀 수 있는 Map으로 만든다.
     */
    private Map<String, Object> productA() {
        ClothingOptions clothing = saved.clothing();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "상품 A");
        body.put("brand", "키들리");
        body.put("categoryId", id(saved.top().getId()));
        body.put("description", "상품 설명");
        body.put("thumbnailUrl", null);
        body.put("imageUrls", List.of("https://cdn.kidly.test/a-1.jpg"));
        body.put("attributes", new ArrayList<>(List.of(attribute(clothing.mainMaterial().getId(),
            List.of(id(clothing.cotton().getId()))))));
        body.put("options", new ArrayList<>(List.of(
            option(List.of(id(clothing.navy().getId()), id(clothing.size110().getId())), 10, "A-NV-110"),
            option(List.of(id(clothing.navy().getId()), id(clothing.size120().getId())), 0, null))));
        body.put("prices", new ArrayList<>(List.of(price(List.of(), 25000))));
        body.put("sizeSpecs", new ArrayList<>(List.of(
            sizeSpec(clothing.size110().getId(), 105, 115, 42, 30, 34, 38),
            sizeSpec(clothing.size120().getId(), 115, 125, 45, 32, 36, 41))));
        return body;
    }

    private static Map<String, Object> attribute(Long attributeId, List<String> valueIds) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("attributeId", id(attributeId));
        attribute.put("valueIds", valueIds);
        attribute.put("text", null);
        return attribute;
    }

    private static Map<String, Object> option(List<String> valueIds, int initialQuantity, String skuCode) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("valueIds", valueIds);
        option.put("initialQuantity", initialQuantity);
        option.put("skuCode", skuCode);
        return option;
    }

    private static Map<String, Object> price(List<String> valueIds, long price) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("valueIds", valueIds);
        group.put("price", price);
        return group;
    }

    private static Map<String, Object> sizeSpec(Long sizeValueId, int minHeightCm, int maxHeightCm,
        int totalLength, int shoulderWidth, int chestWidth, int sleeveLength) {
        Map<String, Object> measurements = new LinkedHashMap<>();
        measurements.put("TOTAL_LENGTH", totalLength);
        measurements.put("SHOULDER_WIDTH", shoulderWidth);
        measurements.put("CHEST_WIDTH", chestWidth);
        measurements.put("SLEEVE_LENGTH", sleeveLength);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("sizeValueId", id(sizeValueId));
        spec.put("minHeightCm", minHeightCm);
        spec.put("maxHeightCm", maxHeightCm);
        spec.put("measurements", measurements);
        return spec;
    }

    // 응답과 같이 요청의 ID도 문자열로 보낸다
    private static String id(Long id) {
        return String.valueOf(id);
    }
}
