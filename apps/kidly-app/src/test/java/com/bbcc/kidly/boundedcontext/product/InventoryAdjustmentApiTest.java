package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * 판매자가 옵션 하나의 재고를 증감량과 사유로 조정하는 API를 HTTP 요청부터 응답까지 확인한다.
 * 옵션은 판매를 시작한 상품의 옵션으로 저장하고 재고를 연다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class InventoryAdjustmentApiTest {

    private static final long OTHER_SELLER_ID = 99L;
    private static final long MISSING_OPTION_ID = 999_999L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProductOption option;

    @BeforeEach
    void saveOptionWithStock5() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(5, 10);
        option = options.navy110();
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
        "RESTOCK, 10, 15", "RESTOCK, 1000000, 1000005", "CORRECTION, 3, 8", "CORRECTION, -3, 2",
        "DEFECTIVE, -1, 4", "LOST, -5, 0"})
    @DisplayName("INV-17 사유와 부호가 맞는 조정이면 판매 가능 수량이 증감량만큼 바뀌고 재고 조정 기록이 한 행 생긴다")
    void adjustStock(String reason, int deltaQuantity, int expectedQty) {
        adjust(tokens.seller(SELLER_ID), option.getId(), Map.of("deltaQuantity", deltaQuantity, "reason", reason))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.optionId").isEqualTo(String.valueOf(option.getId()))
            .jsonPath("$.reason").isEqualTo(reason)
            .jsonPath("$.deltaQuantity").isEqualTo(deltaQuantity)
            .jsonPath("$.availableQuantity").isEqualTo(expectedQty);

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(expectedQty);
        assertThat(sellerAdjustments(option)).containsExactly(tuple(reason, deltaQuantity, SELLER_ID));
    }

    // -1000000은 요청 검증의 범위 안이라 400이 아니라 판매 가능 수량 검사까지 간다
    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-6, -1_000_000})
    @DisplayName("INV-03 판매 가능 수량 5인 옵션을 5보다 많이 줄이면 409로 거절하고 판매 가능 수량과 재고 조정 기록이 그대로다")
    void rejectDecreaseOverAvailable(int deltaQuantity) {
        expectProblem(
            adjust(tokens.seller(SELLER_ID), option.getId(), Map.of("deltaQuantity", deltaQuantity, "reason", "LOST")),
            HttpStatus.CONFLICT, "판매 가능 수량보다 많이 줄일 수 없습니다",
            "판매 가능 수량보다 많이 줄일 수 없습니다. 지금 판매 가능 수량: 5", option.getId());

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestsAgainstAdjustmentRule")
    @DisplayName("INV-18 증감량이 0이거나 사유와 부호가 맞지 않거나 정해지지 않은 사유면 400으로 거절하고 아무것도 바꾸지 않는다")
    void rejectRequestAgainstAdjustmentRule(Map<String, Object> body, String detail) {
        expectBadRequest(body, detail);
    }

    @Test
    @DisplayName("INV-19 옵션을 등록할 때만 남기는 INITIAL 사유로 조정하면 400으로 거절하고 아무것도 바꾸지 않는다")
    void rejectInitialReason() {
        expectBadRequest(Map.of("deltaQuantity", 3, "reason", "INITIAL"),
            "reason: 재고 조정에 쓸 수 없는 사유입니다: INITIAL");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteRequests")
    @DisplayName("증감량이나 사유가 없거나 증감량이 -1000000 이상 1000000 이하 범위를 넘으면 400으로 거절하고 아무것도 바꾸지 않는다")
    void rejectIncompleteRequest(Map<String, Object> body, String detail) {
        expectBadRequest(body, detail);
    }

    @Test
    @DisplayName("다른 판매자의 옵션 재고를 조정하면 403으로 거절하고 아무것도 바꾸지 않는다")
    void rejectOtherSellersOption() {
        expectProblem(
            adjust(tokens.seller(OTHER_SELLER_ID), option.getId(), Map.of("deltaQuantity", 10, "reason", "RESTOCK")),
            HttpStatus.FORBIDDEN, "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다.", option.getId());

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    @Test
    @DisplayName("없는 옵션의 재고를 조정하면 404로 거절한다")
    void rejectMissingOption() {
        expectProblem(
            adjust(tokens.seller(SELLER_ID), MISSING_OPTION_ID, Map.of("deltaQuantity", 10, "reason", "RESTOCK")),
            HttpStatus.NOT_FOUND, "옵션을 찾을 수 없습니다", "옵션을 다시 확인해 주세요.", MISSING_OPTION_ID);
    }

    @Test
    @DisplayName("로그인하지 않으면 401, 판매자가 아닌 회원이면 403으로 거절하고 아무것도 바꾸지 않는다")
    void rejectWithoutSellerRole() {
        Map<String, Object> restock = Map.of("deltaQuantity", 10, "reason", "RESTOCK");

        adjust(null, option.getId(), restock).expectStatus().isUnauthorized();
        adjust(tokens.buyer(SELLER_ID), option.getId(), restock).expectStatus().isForbidden();

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"product, SALE_PENDING", "product, STOPPED", "product_option, STOPPED", "product_option, ARCHIVED"})
    @DisplayName("판매를 시작하기 전이거나 판매를 멈춘 상품, 판매를 멈추거나 보관한 옵션도 재고를 조정할 수 있다")
    void adjustOptionNotOnSale(String table, String status) {
        // 판매 상태를 이렇게 바꾸는 기능이 아직 없어서 상품이나 옵션의 판매 상태를 DB에서 직접 바꾼다
        if (table.equals("product")) {
            jdbcTemplate.update("update product.product set status = ?"
                + " where id = (select product_id from product.product_option where id = ?)", status, option.getId());
        } else {
            jdbcTemplate.update(
                "update product.product_option set sale_status = ? where id = ?", status, option.getId());
        }

        adjust(tokens.seller(SELLER_ID), option.getId(), Map.of("deltaQuantity", 10, "reason", "RESTOCK"))
            .expectStatus().isOk();

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(15);
        assertThat(sellerAdjustments(option)).containsExactly(tuple("RESTOCK", 10, SELLER_ID));
    }

    static Stream<Arguments> requestsAgainstAdjustmentRule() {
        return Stream.of(
            arguments(named("증감량 0", Map.of("deltaQuantity", 0, "reason", "CORRECTION")),
                "deltaQuantity: 증감량은 0이 아니어야 합니다."),
            arguments(named("입고에 음수", Map.of("deltaQuantity", -3, "reason", "RESTOCK")),
                "deltaQuantity: RESTOCK 사유는 증감량이 0보다 커야 합니다."),
            arguments(named("불량에 양수", Map.of("deltaQuantity", 3, "reason", "DEFECTIVE")),
                "deltaQuantity: DEFECTIVE 사유는 증감량이 0보다 작아야 합니다."),
            arguments(named("정해지지 않은 사유", Map.of("deltaQuantity", 3, "reason", "GIFT")),
                "reason: 재고 조정에 쓸 수 없는 사유입니다: GIFT"));
    }

    static Stream<Arguments> incompleteRequests() {
        String range = "deltaQuantity: 증감량은 -1000000 이상 1000000 이하여야 합니다.";
        return Stream.of(
            arguments(named("증감량 없음", Map.of("reason", "RESTOCK")), "deltaQuantity: 증감량을 넣어 주세요."),
            arguments(named("사유 없음", Map.of("deltaQuantity", 3)), "reason: 사유를 넣어 주세요."),
            arguments(named("증감량 1000001", Map.of("deltaQuantity", 1_000_001, "reason", "RESTOCK")), range),
            arguments(named("증감량 -1000001", Map.of("deltaQuantity", -1_000_001, "reason", "LOST")), range));
    }

    // 400으로 거절하고 판매 가능 수량과 재고 조정 기록을 바꾸지 않았는지 본다
    private void expectBadRequest(Map<String, Object> body, String detail) {
        expectProblem(adjust(tokens.seller(SELLER_ID), option.getId(), body),
            HttpStatus.BAD_REQUEST, "잘못된 요청입니다", detail, option.getId());

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    // 실패 응답이 ProblemDetail 5필드(type, title, status, detail, instance)인지 본다
    private void expectProblem(ResponseSpec response, HttpStatus status, String title, String detail, long optionId) {
        response
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(adjustmentsPath(optionId));
    }

    private ResponseSpec adjust(String token, long optionId, Map<String, Object> body) {
        return client.post().uri(adjustmentsPath(optionId))
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private static String adjustmentsPath(long optionId) {
        return "/api/v1/seller/options/" + optionId + "/inventory-adjustments";
    }

    // 옵션을 등록할 때 남긴 INITIAL 기록을 빼고, 판매자가 조정한 기록을 (사유, 증감량, 조정한 판매자)로 읽는다
    private List<Tuple> sellerAdjustments(ProductOption option) {
        return jdbcTemplate.query(
            "select reason, delta_qty, adjusted_by from product.inventory_adjustment_record"
                + " where product_option_id = ? and reason <> 'INITIAL' order by id",
            (rs, rowNum) -> tuple(rs.getString("reason"), rs.getInt("delta_qty"), rs.getLong("adjusted_by")),
            option.getId());
    }
}
