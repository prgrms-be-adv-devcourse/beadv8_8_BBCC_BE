package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

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
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String productId;

    @BeforeEach
    void registerProductA() {
        SavedClothingTop saved = clothingTopSaver.save();
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
    @DisplayName("SALE-03 · SALE-08 판매대기 상품의 판매를 시작하면 판매중이 되고, 커밋 뒤 상품 정보 변경됨이 한 번 나간다")
    void startSaleOfPendingProduct() {
        startSale(tokens.seller(SELLER_ID), ON_SALE)
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
            // 등록 요청의 초기 재고가 110은 10개, 120은 0개라 120만 품절이다
            assertThat(event.data().options())
                .extracting(ProductChangedEvent.Option::size, ProductChangedEvent.Option::soldOut,
                    ProductChangedEvent.Option::saleStatus)
                .containsExactly(tuple("110", false, OptionSaleStatus.ON_SALE),
                    tuple("120", true, OptionSaleStatus.ON_SALE));
        });
    }

    @Test
    @DisplayName("이미 판매중인 상품에 판매 시작을 다시 보내면 200이고, 상태 · version이 그대로이며 이벤트도 다시 나가지 않는다")
    void startSaleAgainChangesNothing() {
        startSale(tokens.seller(SELLER_ID), ON_SALE).expectStatus().isOk();
        long versionAfterFirst = storedVersion();

        for (int i = 0; i < 2; i++) {
            startSale(tokens.seller(SELLER_ID), ON_SALE)
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

        expectProblem(startSale(tokens.seller(SELLER_ID), ON_SALE), HttpStatus.CONFLICT,
            "판매를 시작할 수 없습니다", "판매를 시작할 수 없습니다. 120 사이즈에 실측과 권장 키가 없습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("OPT-08 다른 판매자의 상품이면 403이고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectOtherSellersProduct() {
        expectProblem(startSale(tokens.seller(OTHER_SELLER_ID), ON_SALE), HttpStatus.FORBIDDEN,
            "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("없는 상품이면 404다")
    void rejectMissingProduct() {
        productId = "999999";

        expectProblem(startSale(tokens.seller(SELLER_ID), ON_SALE), HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("토큰이 없으면 401, 판매자가 아닌 회원이면 403이다")
    void rejectWithoutSellerRole() {
        startSale(null, ON_SALE).expectStatus().isUnauthorized();
        startSale(tokens.buyer(SELLER_ID), ON_SALE).expectStatus().isForbidden();
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SALE_PENDING", "STOPPED", "FOO"})
    @DisplayName("판매 시작(ON_SALE)이 아닌 판매 상태를 보내면 400이고, 상태는 판매대기로 남고 이벤트도 나가지 않는다")
    void rejectStatusOtherThanOnSale(String status) {
        expectProblem(startSale(tokens.seller(SELLER_ID), Map.of("status", status)), HttpStatus.BAD_REQUEST,
            "잘못된 요청입니다", "status: 판매 시작(ON_SALE)만 요청할 수 있습니다.");
        assertThat(storedStatus()).isEqualTo("SALE_PENDING");
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 상품에 판매 시작 요청이 동시에 여러 개 와도 판매중으로 한 번만 바뀌고 이벤트도 한 번만 나가며, 나머지는 200이나 409다")
    void concurrentStartSaleChangesOnce() throws Exception {
        int requestCount = 5;
        String token = tokens.seller(SELLER_ID);
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
                return startSale(token, ON_SALE).returnResult(String.class).getStatus().value();
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

    private RestTestClient.ResponseSpec startSale(String token, Map<String, Object> body) {
        return client.put().uri(PRODUCTS_PATH + "/{productId}/sale-status", productId)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
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
