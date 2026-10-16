package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.attributes;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.id;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.measurements;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.ownImageUrl;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.prices;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.sizeSpecs;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    // 어느 표에도 저장하지 않은 ID다
    private static final String UNKNOWN_ID = "999999";

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
            .body(productA(saved))
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
    @DisplayName("INV-02 · INV-18 · INV-19 옵션마다 초기 재고로 재고 행을 만들고, 0이 아니면 INITIAL 기록을 남겨 수량과 기록 합이 같다")
    void saveInventoryWithInitialRecord() {
        register(productA(saved)).expectStatus().isCreated();
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
        assertThat(entityManager.createQuery(ProductRegistrationRepeatApiTest.AVAILABLE_AND_RECORDED_QTY,
                Object[].class).getResultList())
            .allSatisfy(ProductRegistrationRepeatApiTest::assertAvailableEqualsRecorded);
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectWithoutToken() {
        client.post().uri(PATH)
            .body(productA(saved))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("판매자가 아닌 회원은 403을 받는다")
    void rejectBuyer() {
        client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(SELLER_ID)))
            .body(productA(saved))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("없는 카테고리로 등록하면 404를 받는다")
    void rejectUnknownCategory() {
        Map<String, Object> body = productA(saved);
        body.put("categoryId", UNKNOWN_ID);

        expectProblem(register(body), HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다", "카테고리를 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("TYPE-01 중간 카테고리로는 등록할 수 없다")
    void rejectMiddleCategory() {
        Map<String, Object> body = productA(saved);
        body.put("categoryId", id(saved.clothingCategory().getId()));

        expectProblem(register(body), HttpStatus.BAD_REQUEST, "상품을 등록할 수 없는 카테고리입니다",
            "가장 아래 단계의 카테고리를 골라 주세요.");
    }

    @Test
    @DisplayName("OPT-03 요청 안에서 같은 값 묶음의 옵션이 겹치면 409를 받는다")
    void rejectDuplicatedOptionInRequest() {
        Map<String, Object> body = productA(saved);
        options(body).add(options(body).get(0));

        expectProblem(register(body), HttpStatus.CONFLICT, "이미 있는 옵션입니다",
            "같은 값 묶음의 옵션은 하나만 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("SALE-03 옵션이 쓰는 사이즈의 실측 · 권장 키가 빠지면 빠진 것을 모두 적은 400을 받고 아무것도 저장하지 않는다")
    void rejectMissingSaleStartInputs() {
        Map<String, Object> body = productA(saved);
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

    @Test
    @DisplayName("SALE-03 옵션을 하나도 보내지 않으면 400을 받는다")
    void rejectWithoutOptions() {
        Map<String, Object> body = productA(saved);
        options(body).clear();
        sizeSpecs(body).clear();

        expectProblem(register(body), HttpStatus.BAD_REQUEST, "판매 시작에 필요한 값이 빠졌습니다",
            "옵션이 하나 이상 있어야 합니다.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownIds")
    @DisplayName("INFO-03 · OPT-04 카테고리 상품 유형에 없는 항목이나 값의 ID를 보내면 400을 받는다")
    void rejectIdNotInProductType(Consumer<Map<String, Object>> change, String title) {
        Map<String, Object> body = productA(saved);
        change.accept(body);

        register(body)
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo(title);
    }

    static Stream<Arguments> unknownIds() {
        return Stream.of(
            invalid("정보 항목", body -> attributes(body).get(0).put("attributeId", UNKNOWN_ID),
                "이 상품 유형의 정보 항목이 아닙니다"),
            invalid("정보 값", body -> attributes(body).get(0).put("valueIds", List.of(UNKNOWN_ID)), "허용되지 않은 값입니다"),
            invalid("옵션 값", body -> options(body).get(0).put("valueIds", List.of(UNKNOWN_ID)), "허용되지 않은 옵션 값입니다"),
            invalid("가격 구분 값", body -> prices(body).get(0).put("valueIds", List.of(UNKNOWN_ID)),
                "가격 구분 묶음의 가격이 맞지 않습니다"),
            invalid("실측의 사이즈 값", body -> sizeSpecs(body).get(0).put("sizeValueId", UNKNOWN_ID), "옵션에 없는 사이즈입니다"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    @DisplayName("요청 값의 형식이나 범위가 틀리면 필드 이름을 앞에 붙인 400을 받는다")
    void rejectInvalidInput(Consumer<Map<String, Object>> change, String detail) {
        Map<String, Object> body = productA(saved);
        change.accept(body);

        register(body)
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail").isEqualTo(detail);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
            invalid("빈 상품명", body -> body.put("name", " "), "name: 상품명을 입력해 주세요."),
            invalid("100자를 넘는 상품명", body -> body.put("name", "가".repeat(101)),
                "name: 상품명은 100자 이하로 입력해 주세요."),
            invalid("255자를 넘는 브랜드", body -> body.put("brand", "가".repeat(256)),
                "brand: 브랜드는 255자 이하로 입력해 주세요."),
            invalid("카테고리 없음", body -> body.put("categoryId", null), "categoryId: 카테고리를 골라 주세요."),
            invalid("이미지 목록 없음", body -> body.put("imageUrls", null), "imageUrls: 이미지 목록이 필요합니다."),
            invalid("이미지 목록에 빈 칸", body -> body.put("imageUrls", Arrays.asList(ownImageUrl(SELLER_ID), null)),
                "imageUrls[1]: 이미지 주소를 넣어 주세요."),
            invalid("정보 항목 목록 없음", body -> body.put("attributes", null), "attributes: 정보 항목 목록이 필요합니다."),
            invalid("옵션 목록 없음", body -> body.put("options", null), "options: 옵션 목록이 필요합니다."),
            invalid("가격 목록 없음", body -> body.put("prices", null), "prices: 가격 목록이 필요합니다."),
            invalid("실측 목록 없음", body -> body.put("sizeSpecs", null), "sizeSpecs: 실측 목록이 필요합니다."),
            invalid("음수 초기 재고", body -> options(body).get(0).put("initialQuantity", -1),
                "options[0].initialQuantity: 초기 재고는 0 이상이어야 합니다."),
            invalid("255자를 넘는 SKU 코드", body -> options(body).get(0).put("skuCode", "A".repeat(256)),
                "options[0].skuCode: SKU 코드는 255자 이하로 입력해 주세요."),
            invalid("빈 SKU 코드", body -> options(body).get(0).put("skuCode", ""),
                "options[0].skuCode: SKU 코드는 공백만으로 넣을 수 없습니다."),
            invalid("공백만 있는 SKU 코드", body -> options(body).get(0).put("skuCode", "  "),
                "options[0].skuCode: SKU 코드는 공백만으로 넣을 수 없습니다."),
            invalid("전각 공백만 있는 SKU 코드", body -> options(body).get(0).put("skuCode", "　"),
                "options[0].skuCode: SKU 코드는 공백만으로 넣을 수 없습니다."),
            invalid("판매가 0", body -> prices(body).get(0).put("price", 0), "prices[0].price: 판매가는 0보다 커야 합니다."),
            invalid("사이즈 값 없음", body -> sizeSpecs(body).get(0).put("sizeValueId", null),
                "sizeSpecs[0].sizeValueId: 사이즈 값을 골라 주세요."),
            invalid("권장 키 0", body -> sizeSpecs(body).get(0).put("minHeightCm", 0),
                "sizeSpecs[0].minHeightCm: 권장 키는 0보다 커야 합니다."),
            invalid("실측 값 0", body -> measurements(sizeSpecs(body).get(0)).put("TOTAL_LENGTH", 0),
                "sizeSpecs[0].measurements[TOTAL_LENGTH]: 실측 값은 0보다 커야 합니다."));
    }

    private static Arguments invalid(String name, Consumer<Map<String, Object>> change, String detail) {
        return Arguments.of(Named.of(name, change), detail);
    }

    private RestTestClient.ResponseSpec register(Map<String, Object> body) {
        return client.post().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .body(body)
            .exchange();
    }

    @Test
    @DisplayName("썸네일과 이미지 목록이 판매자 본인이 올린 이미지이면 등록한다")
    void registerWithOwnImages() {
        Map<String, Object> body = productA(saved);
        body.put("thumbnailUrl", ownImageUrl(SELLER_ID));
        body.put("imageUrls", List.of(ownImageUrl(SELLER_ID)));

        register(body).expectStatus().isCreated();

        Product product = entityManager.createQuery("select p from Product p", Product.class).getSingleResult();
        assertThat(product.getThumbnailUrl()).isEqualTo(ownImageUrl(SELLER_ID));
        assertThat(product.getImageUrls()).containsExactly(ownImageUrl(SELLER_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOwnImages")
    @DisplayName("썸네일이나 이미지 목록이 판매자 본인이 올린 이미지가 아니면 400을 받고 아무것도 저장하지 않는다")
    void rejectNotOwnImage(Consumer<Map<String, Object>> change) {
        Map<String, Object> body = productA(saved);
        change.accept(body);

        expectProblem(register(body), HttpStatus.BAD_REQUEST, "허용되지 않은 이미지 주소입니다",
            "상품 이미지는 업로드 주소로 올린 본인 이미지만 넣을 수 있습니다. 이미지를 업로드 주소로 올린 뒤 받은 이미지 주소를 넣어 주세요.");
        assertThat(count("Product")).isZero();
        assertThat(count("Inventory")).isZero();
    }

    static Stream<Arguments> notOwnImages() {
        return Stream.of(
            Arguments.of(Named.<Consumer<Map<String, Object>>>of("다른 판매자의 썸네일",
                body -> body.put("thumbnailUrl", ownImageUrl(SELLER_ID + 1)))),
            Arguments.of(Named.<Consumer<Map<String, Object>>>of("외부 이미지 목록",
                body -> body.put("imageUrls", List.of("https://cdn.kidly.test/a-1.jpg")))));
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
}
