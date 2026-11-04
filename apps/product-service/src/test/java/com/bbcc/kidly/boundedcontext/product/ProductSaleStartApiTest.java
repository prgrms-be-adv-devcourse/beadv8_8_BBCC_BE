package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.support.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판매자가 판매대기 상품의 판매를 시작하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 상품은 등록 API로 판매대기로 만들고, 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class ProductSaleStartApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;
    private static final long OTHER_SELLER_ID = 8L;
    private static final Map<String, Object> ON_SALE = Map.of("status", "ON_SALE");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private SavedClothingTop saved;

    private String productId;

    @BeforeEach
    void registerProductA() {
        saved = clothingTopSaver.save();
        productId = client.post().uri(PRODUCTS_PATH)
            .headers(TestMembers.seller(SELLER_ID))
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
    @DisplayName("SALE-03 · SALE-08 판매대기 상품의 판매를 시작하면 판매중이 되고, 커밋 뒤 상품 정보 변경됨이 한 번 나간다")
    void startSaleOfPendingProduct() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId)
            .jsonPath("$.status").isEqualTo("ON_SALE")
            .jsonPath("$.version").isEqualTo(storedVersion());

        assertThat(storedStatus()).isEqualTo("ON_SALE");
        List<ProductChangedEvent> events = eventRecorder.events(ProductChangedEvent.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.data().productId()).isEqualTo(Long.valueOf(productId));
            assertThat(event.data().status()).isEqualTo(ProductStatus.ON_SALE);
            // 등록 요청의 초기 재고가 110은 10개, 120은 0개라 120만 품절이다.
            // 권장 키는 DB에서 읽은 옵션과 실측의 사이즈 값이 같은 객체여야 채워지므로, 저장한 상품으로 다시 확인한다
            assertThat(event.data().options())
                .extracting(ProductChangedEvent.Option::size, ProductChangedEvent.Option::minHeightCm,
                    ProductChangedEvent.Option::maxHeightCm, ProductChangedEvent.Option::soldOut,
                    ProductChangedEvent.Option::saleStatus)
                .containsExactly(tuple("110", 105, 115, false, OptionSaleStatus.ON_SALE),
                    tuple("120", 115, 125, true, OptionSaleStatus.ON_SALE));
        });
    }

    @Test
    @DisplayName("SALE-08 판매 시작으로 나간 상품 정보 변경됨에 저장된 옵션 값과 상품 정보 값이 항목 코드 · 값 ID와 함께 들어 있다")
    void startSaleEventCarriesAttributeValues() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE).expectStatus().isOk();

        // 항목은 지연 로딩이라, 판매 시작 트랜잭션 안에서 항목 코드와 값 ID를 읽는지 저장한 상품으로 확인한다
        ClothingOptions clothing = saved.clothing();
        ProductChangedEvent.Data data = eventRecorder.events(ProductChangedEvent.class).getFirst().data();
        assertThat(data.options()).satisfiesExactly(
            option110 -> assertThat(option110.values()).containsExactlyInAnyOrder(
                new ProductChangedEvent.AttributeEntry("COLOR", clothing.navy().getId(), "네이비"),
                new ProductChangedEvent.AttributeEntry("SIZE", clothing.size110().getId(), "110")),
            option120 -> assertThat(option120.values()).containsExactlyInAnyOrder(
                new ProductChangedEvent.AttributeEntry("COLOR", clothing.navy().getId(), "네이비"),
                new ProductChangedEvent.AttributeEntry("SIZE", clothing.size120().getId(), "120")));
        assertThat(data.infoValues()).containsExactly(
            new ProductChangedEvent.AttributeEntry("MAIN_MATERIAL", clothing.cotton().getId(), "면"));
    }

    @Test
    @DisplayName("SALE-08 판매 시작으로 나간 상품 정보 변경됨에 브랜드 · 설명과 저장된 사이즈별 실측이 들어 있다")
    void startSaleEventCarriesBrandDescriptionAndSizeSpecs() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE).expectStatus().isOk();

        // 실측은 JSONB로 저장했다가 판매 시작 트랜잭션에서 다시 읽은 값이다. 숫자 자릿수와 상관없이 값으로 비교한다
        ProductChangedEvent.Data data = eventRecorder.events(ProductChangedEvent.class).getFirst().data();
        assertThat(data.brand()).isEqualTo("키들리");
        assertThat(data.description()).isEqualTo("상품 설명");
        assertThat(data.sizeSpecs()).usingRecursiveFieldByFieldElementComparator(
                RecursiveComparisonConfiguration.builder()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).build())
            .containsExactly(
                new ProductChangedEvent.SizeSpec("110", 105, 115, measurements(42, 30, 34, 38)),
                new ProductChangedEvent.SizeSpec("120", 115, 125, measurements(45, 32, 36, 41)));
    }

    @Test
    @DisplayName("SALE-08 판매 시작으로 아웃박스에 남은 본문의 data에 브랜드 · 설명 · 사이즈별 실측이 있고, 다시 읽으면 나간 이벤트와 같다")
    void startSaleOutboxPayloadCarriesNewFields() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE).expectStatus().isOk();

        ProductChangedEvent.Data published = eventRecorder.events(ProductChangedEvent.class).getFirst().data();
        JsonNode payloadData = jsonMapper.readTree(
            jdbcTemplate.queryForObject("select payload from product.product_outbox", String.class)).get("data");
        assertThat(payloadData.get("brand").asString()).isEqualTo("키들리");
        assertThat(payloadData.get("description").asString()).isEqualTo("상품 설명");
        assertThat(payloadData.get("sizeSpecs").get(0).get("measurements").propertyNames())
            .containsExactlyInAnyOrder("TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        assertThat(jsonMapper.treeToValue(payloadData, ProductChangedEvent.Data.class)).isEqualTo(published);
    }

    @Test
    @DisplayName("판매 시작이 커밋되면 나간 상품 정보 변경됨이 상품 ID를 키로 아웃박스에 한 줄 남는다")
    void startSaleStoresOutboxMessage() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE).expectStatus().isOk();

        UUID eventId = eventRecorder.events(ProductChangedEvent.class).getFirst().eventId();
        assertThat(jdbcTemplate.queryForList(
            "select event_id, event_type, topic, message_key, sent_at from product.product_outbox"))
            .singleElement().satisfies(row -> assertThat(row)
                .containsEntry("event_id", eventId)
                .containsEntry("event_type", "product.changed")
                .containsEntry("topic", "kidly.product.events")
                .containsEntry("message_key", productId)
                .containsEntry("sent_at", null));
    }

    @Test
    @DisplayName("이미 판매중인 상품에 판매 시작을 다시 보내면 200이고, 상태 · version이 그대로이며 이벤트도 다시 나가지 않는다")
    void startSaleAgainChangesNothing() {
        startSale(TestMembers.seller(SELLER_ID), ON_SALE).expectStatus().isOk();
        long versionAfterFirst = storedVersion();

        for (int i = 0; i < 2; i++) {
            startSale(TestMembers.seller(SELLER_ID), ON_SALE)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ON_SALE")
                .jsonPath("$.version").isEqualTo(versionAfterFirst);
        }

        assertThat(storedVersion()).isEqualTo(versionAfterFirst);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("SALE-03 판매 시작 조건을 채우지 못하면 빠진 항목을 적어 409로 거절하고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectWhenSaleStartConditionNotMet() {
        // 등록할 때 같은 검사를 하므로, 등록한 뒤 120 사이즈의 실측을 지워 조건을 못 채우게 한다
        jdbcTemplate.update("delete from product.size_spec where product_id = ? and attribute_value_id = "
            + "(select id from product.attribute_value where name = '120')", Long.valueOf(productId));

        expectProblem(startSale(TestMembers.seller(SELLER_ID), ON_SALE), HttpStatus.CONFLICT,
            "판매를 시작할 수 없습니다", "판매를 시작할 수 없습니다. 120 사이즈에 실측과 권장 키가 없습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("OPT-08 다른 판매자의 상품이면 403이고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectOtherSellersProduct() {
        expectProblem(startSale(TestMembers.seller(OTHER_SELLER_ID), ON_SALE), HttpStatus.FORBIDDEN,
            "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("없는 상품이면 404다")
    void rejectMissingProduct() {
        productId = "999999";

        expectProblem(startSale(TestMembers.seller(SELLER_ID), ON_SALE), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SALE_PENDING", "FOO"})
    @DisplayName("판매 시작(ON_SALE)이나 판매 중지(STOPPED)가 아닌 판매 상태를 보내면 400이고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectStatusOtherThanOnSale(String status) {
        expectProblem(startSale(TestMembers.seller(SELLER_ID), Map.of("status", status)), HttpStatus.BAD_REQUEST,
            "잘못된 요청입니다", "status: 판매 시작(ON_SALE)이나 판매 중지(STOPPED)만 요청할 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 상품에 판매 시작 요청이 동시에 여러 개 와도 판매중으로 한 번만 바뀌고 이벤트도 한 번만 나가며, 나머지는 200이나 409다")
    void concurrentStartSaleChangesOnce() throws Exception {
        int requestCount = 5;
        var login = TestMembers.seller(SELLER_ID);
        // 모든 요청이 준비된 뒤 같은 순간에 출발하게 한다
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            requests.add(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("출발 신호를 기다리다 시간이 지났습니다");
                }
                return startSale(login, ON_SALE).returnResult(String.class).getStatus().value();
            });
        }
        List<Integer> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> request : requests) {
                results.add(executor.submit(request));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(statuses).allMatch(status -> status == 200 || status == 409).contains(200);
        assertThat(storedStatus()).isEqualTo("ON_SALE");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("판매 상태를 보내지 않으면 400이다")
    void rejectMissingStatus() {
        expectProblem(startSale(TestMembers.seller(SELLER_ID), Map.of()), HttpStatus.BAD_REQUEST,
            "잘못된 요청입니다", "status: 판매 시작(ON_SALE)이나 판매 중지(STOPPED)만 요청할 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
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
            .jsonPath("$.instance").isEqualTo(PRODUCTS_PATH + "/" + productId + "/sale-status");
    }

    // 등록 요청의 실측 값(총장 · 어깨너비 · 가슴단면 · 소매길이, cm)을 실측 항목 코드 맵으로 만든다
    private static Map<String, BigDecimal> measurements(int totalLength, int shoulderWidth, int chestWidth,
        int sleeveLength) {
        return Map.of(
            "TOTAL_LENGTH", BigDecimal.valueOf(totalLength),
            "SHOULDER_WIDTH", BigDecimal.valueOf(shoulderWidth),
            "CHEST_WIDTH", BigDecimal.valueOf(chestWidth),
            "SLEEVE_LENGTH", BigDecimal.valueOf(sleeveLength));
    }

    private RestTestClient.ResponseSpec startSale(Consumer<HttpHeaders> login, Map<String, Object> body) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private String storedStatus() {
        return jdbcTemplate.queryForObject(
            "select status from product.product where id = ?", String.class, Long.valueOf(productId));
    }

    private long storedVersion() {
        return jdbcTemplate.queryForObject(
            "select version from product.product where id = ?", Long.class, Long.valueOf(productId));
    }
}
