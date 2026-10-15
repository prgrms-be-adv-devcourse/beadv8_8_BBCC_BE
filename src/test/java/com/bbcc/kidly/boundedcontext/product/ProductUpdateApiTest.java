package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.id;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.options;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.sizeSpecs;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of(Named.of("상품명이 공백뿐", (Consumer<Map<String, Object>>) body -> body.put("name", " ")),
                "name: 상품명을 입력해 주세요."),
            Arguments.of(Named.of("상품명이 101자",
                    (Consumer<Map<String, Object>>) body -> body.put("name", "가".repeat(101))),
                "name: 상품명은 100자 이하로 입력해 주세요."),
            Arguments.of(Named.of("version이 없음", (Consumer<Map<String, Object>>) body -> body.put("version", null)),
                "version: version이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("요청 값이 형식을 벗어나면 400 ProblemDetail과 칸 이름 · 사유를 담은 detail을 받는다")
    void rejectInvalidRequest(Consumer<Map<String, Object>> breakBody, String detail) {
        Map<String, Object> body = updateBody(storedVersion());
        breakBody.accept(body);

        expectProblem(update(tokens.seller(SELLER_ID), body), HttpStatus.BAD_REQUEST, "잘못된 요청입니다", detail);
    }

    @Test
    @DisplayName("옵션 · 가격 · 실측 중 일부만 보내면 400 ProblemDetail을 받는다")
    void rejectIncompleteSaleInputs() {
        Map<String, Object> body = updateBody(storedVersion());
        body.put("options", productA(saved).get("options"));

        expectProblem(update(tokens.seller(SELLER_ID), body), HttpStatus.BAD_REQUEST,
            "옵션 · 가격 · 실측이 함께 오지 않았습니다", "옵션 · 가격 · 실측을 모두 보내거나 모두 빼 주세요.");
    }

    @Test
    @DisplayName("판매를 시작한 상품에 옵션 · 가격 · 실측을 보내면 409 ProblemDetail을 받는다")
    void rejectSaleInputsAfterSaleStart() {
        startSale();
        Map<String, Object> body = productA(saved);
        body.put("version", storedVersion());

        expectProblem(update(tokens.seller(SELLER_ID), body), HttpStatus.CONFLICT,
            "판매를 시작한 상품의 옵션 · 가격 · 실측은 여기서 바꿀 수 없습니다", "판매를 시작한 상품은 옵션 · 가격 · 실측을 빼고 보내 주세요.");
    }

    @Test
    @DisplayName("판매를 시작한 상품의 카테고리를 같은 유형의 다른 카테고리로 바꾸면 409 ProblemDetail을 받는다")
    void rejectCategoryChangeAfterSaleStart() {
        startSale();
        Map<String, Object> body = updateBody(storedVersion());
        body.put("categoryId", id(savedOuter()));

        expectProblem(update(tokens.seller(SELLER_ID), body), HttpStatus.CONFLICT,
            "판매를 시작한 상품은 카테고리를 바꿀 수 없습니다", "판매를 시작한 상품은 지금 카테고리를 그대로 보내 주세요.");
    }

    @Test
    @DisplayName("판매대기 상품의 옵션을 바꿔 넣으면 뺀 옵션의 재고는 지우고, 새 옵션은 받은 초기 재고로 열고, "
        + "남긴 옵션은 초기 재고 차이만큼 정정 기록을 남긴다. 판매 전이라 상품 정보 변경됨은 나가지 않는다")
    void replaceSaleInputsOfPendingProduct() {
        List<Long> optionIdsBefore = optionIds();
        ClothingOptions clothing = saved.clothing();
        Map<String, Object> body = productA(saved);
        body.put("version", storedVersion());
        body.put("options", new ArrayList<>(List.of(
            option(List.of(id(clothing.navy().getId()), id(clothing.size110().getId())), 7, "A-NV-110"),
            option(List.of(id(clothing.oatmeal().getId()), id(clothing.size110().getId())), 5, null))));
        body.put("sizeSpecs", new ArrayList<>(List.of(sizeSpecs(productA(saved)).get(0))));

        update(tokens.seller(SELLER_ID), body)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.options.length()").isEqualTo(2);

        List<Long> optionIdsAfter = optionIds();
        Long navy110 = optionIdsBefore.get(0);
        Long oatmeal110 = optionIdsAfter.get(1);
        assertThat(optionIdsAfter.get(0)).isEqualTo(navy110);
        assertThat(availableQty(navy110)).isEqualTo(7);
        assertThat(availableQty(oatmeal110)).isEqualTo(5);
        assertThat(inventoryRowCount(optionIdsBefore.get(1))).isZero();
        assertThat(adjustments(navy110)).containsExactly("INITIAL 10", "CORRECTION -3");
        assertThat(adjustments(oatmeal110)).containsExactly("INITIAL 5");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("OPT-07 SKU 코드는 같은 판매자 안에서 겹치지 않아야 하므로, 같은 판매자의 다른 상품이 쓰는 SKU 코드로 바꾸면 "
        + "409 ProblemDetail을 받고 옵션은 그대로다")
    void rejectSkuCodeOfOtherProduct() {
        Map<String, Object> productB = productA(saved);
        productB.put("name", "상품 B");
        options(productB).get(0).put("skuCode", "B-NV-110");
        client.post().uri(PRODUCTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(productB)
            .exchange()
            .expectStatus().isCreated();
        Map<String, Object> body = productA(saved);
        body.put("version", storedVersion());
        options(body).get(1).put("skuCode", "B-NV-110");

        expectProblem(update(tokens.seller(SELLER_ID), body), HttpStatus.CONFLICT, "이미 쓰는 SKU 코드입니다",
            "같은 판매자의 다른 옵션과 겹치지 않게 SKU 코드를 넣어 주세요. 겹친 코드는 B-NV-110입니다.");
        assertThat(jdbcTemplate.queryForList(
            "select sku_code from product.product_option where product_id = ? order by id", String.class,
            Long.valueOf(productId))).containsExactly("A-NV-110", null);
    }

    @Test
    @DisplayName("판매대기 상품에 옵션 · 가격 · 실측은 그대로 두고 초기 재고만 바꿔 보내면, 상품 version은 그대로이고 "
        + "초기 재고 차이만큼 정정 기록을 남긴다")
    void correctInitialQuantityOnly() {
        Long navy110 = optionIds().get(0);
        long version = storedVersion();
        Map<String, Object> body = productA(saved);
        body.put("version", version);
        options(body).get(0).put("initialQuantity", 12);

        update(tokens.seller(SELLER_ID), body).expectStatus().isOk();

        assertThat(storedVersion()).isEqualTo(version);
        assertThat(availableQty(navy110)).isEqualTo(12);
        assertThat(adjustments(navy110)).containsExactly("INITIAL 10", "CORRECTION 2");
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

    // 상의와 같은 유형 · 같은 필수 실측 항목을 쓰는 아우터 카테고리를 저장하고 ID를 돌려준다
    private Long savedOuter() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Category top = entityManager.find(Category.class, saved.top().getId());
            List<MeasurementItem> items = new ArrayList<>();
            for (CategoryMeasurementItem link : top.getMeasurementItems()) {
                items.add(link.getMeasurementItem());
            }
            Category outer = Category.createLeaf(new Category.LeafInfo("아우터",
                entityManager.find(Category.class, saved.clothingCategory().getId()),
                entityManager.find(ProductType.class, saved.clothing().type().getId()), items));
            entityManager.persist(outer);
            return outer.getId();
        });
    }

    private List<Long> optionIds() {
        return jdbcTemplate.queryForList("select id from product.product_option where product_id = ? order by id",
            Long.class, Long.valueOf(productId));
    }

    private int availableQty(Long optionId) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, optionId);
    }

    private long inventoryRowCount(Long optionId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.inventory where product_option_id = ?", Long.class, optionId);
    }

    // 옵션의 재고 조정 기록을 "사유 증감량"으로 남긴 순서대로 읽는다
    private List<String> adjustments(Long optionId) {
        return jdbcTemplate.queryForList("select reason || ' ' || delta_qty from product.inventory_adjustment_record "
            + "where product_option_id = ? order by id", String.class, optionId);
    }

    private long storedVersion() {
        return jdbcTemplate.queryForObject(
            "select version from product.product where id = ?", Long.class, Long.valueOf(productId));
    }
}
