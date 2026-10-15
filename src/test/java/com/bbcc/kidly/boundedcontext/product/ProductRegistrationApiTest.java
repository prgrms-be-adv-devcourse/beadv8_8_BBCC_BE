package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    // 응답에 없는 재고 행 · 재고 조정 기록과, 실패한 요청이 아무것도 저장하지 않았는지를 확인할 때 쓴다
    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("INV-02 · INV-18 · INV-19 옵션마다 초기 재고로 재고 행을 만들고, 초기 재고가 0이 아니면 INITIAL 기록을 남긴다")
    void saveInventoryWithInitialRecord() {
        register(productA()).expectStatus().isCreated();
        List<Long> optionIds = entityManager.createQuery(
            "select o.id from ProductOption o order by o.id", Long.class).getResultList();

        assertThat(entityManager.createQuery(
                "select i.productOption.id, i.availableQty from Inventory i order by i.productOption.id",
                Object[].class).getResultList())
            .containsExactly(new Object[] {optionIds.get(0), 10}, new Object[] {optionIds.get(1), 0});
        assertThat(entityManager.createQuery(
                "select r.productOption.id, r.deltaQty, r.reason, r.adjustedBy from InventoryAdjustmentRecord r",
                Object[].class).getResultList())
            .containsExactly(new Object[] {optionIds.get(0), 10, InventoryAdjustmentReason.INITIAL, SELLER_ID});
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectWithoutToken() {
        client.post().uri(PATH)
            .body(productA())
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("판매자가 아닌 회원은 403을 받는다")
    void rejectBuyer() {
        client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(SELLER_ID)))
            .body(productA())
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("없는 카테고리로 등록하면 404를 받는다")
    void rejectUnknownCategory() {
        Map<String, Object> body = productA();
        body.put("categoryId", "999999");

        expectProblem(register(body), HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다", "카테고리를 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("TYPE-01 중간 카테고리로는 등록할 수 없다")
    void rejectMiddleCategory() {
        Map<String, Object> body = productA();
        body.put("categoryId", id(saved.clothingCategory().getId()));

        expectProblem(register(body), HttpStatus.BAD_REQUEST, "상품을 등록할 수 없는 카테고리입니다",
            "가장 아래 단계의 카테고리를 골라 주세요.");
    }

    @Test
    @DisplayName("OPT-03 요청 안에서 같은 값 묶음의 옵션이 겹치면 409를 받는다")
    void rejectDuplicatedOptionInRequest() {
        Map<String, Object> body = productA();
        options(body).add(options(body).get(0));

        expectProblem(register(body), HttpStatus.CONFLICT, "이미 있는 옵션입니다",
            "같은 값 묶음의 옵션은 하나만 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("SALE-03 옵션이 쓰는 사이즈의 실측 · 권장 키가 빠지면 빠진 것을 모두 적은 400을 받고 아무것도 저장하지 않는다")
    void rejectMissingSaleStartInputs() {
        Map<String, Object> body = productA();
        List<Map<String, Object>> specs = sizeSpecs(body);
        specs.remove(1);
        measurements(specs.get(0)).remove("SLEEVE_LENGTH");

        register(body)
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("판매 시작에 필요한 값이 빠졌습니다")
            .jsonPath("$.detail").value(String.class, detail -> assertThat(detail)
                .contains("110 사이즈에 소매길이가 없습니다.")
                .contains("120 사이즈에 실측과 권장 키가 없습니다."));
        assertThat(count("Product")).isZero();
        assertThat(count("Inventory")).isZero();
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

    private RestTestClient.ResponseSpec register(Map<String, Object> body) {
        return client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .body(body)
            .exchange();
    }

    // 실패 응답이 ProblemDetail 5칸을 모두 담는지 확인한다
    private void expectProblem(RestTestClient.ResponseSpec response, HttpStatus status, String title, String detail) {
        response
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> options(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("options");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sizeSpecs(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("sizeSpecs");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> measurements(Map<String, Object> sizeSpec) {
        return (Map<String, Object>) sizeSpec.get("measurements");
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
