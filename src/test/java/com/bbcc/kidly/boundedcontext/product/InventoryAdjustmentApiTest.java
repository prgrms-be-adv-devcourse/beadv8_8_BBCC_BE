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
import org.junit.jupiter.params.provider.MethodSource;
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

    @Test
    @DisplayName("INV-17 판매자가 입고 10을 조정하면 판매 가능 수량이 10 늘고 재고 조정 기록이 한 행 생긴다")
    void adjustStock() {
        adjust(tokens.seller(SELLER_ID), option.getId(), Map.of("deltaQuantity", 10, "reason", "RESTOCK"))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.optionId").isEqualTo(String.valueOf(option.getId()))
            .jsonPath("$.reason").isEqualTo("RESTOCK")
            .jsonPath("$.deltaQuantity").isEqualTo(10)
            .jsonPath("$.availableQuantity").isEqualTo(15);

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(15);
        assertThat(sellerAdjustments(option)).containsExactly(tuple("RESTOCK", 10, SELLER_ID));
    }

    @Test
    @DisplayName("INV-03 판매 가능 수량 5인 옵션을 6 줄이면 409로 거절하고 판매 가능 수량과 재고 조정 기록이 그대로다")
    void rejectDecreaseOverAvailable() {
        expectProblem(
            adjust(tokens.seller(SELLER_ID), option.getId(), Map.of("deltaQuantity", -6, "reason", "LOST")),
            HttpStatus.CONFLICT, "판매 가능 수량보다 많이 줄일 수 없습니다",
            "판매 가능 수량보다 많이 줄일 수 없습니다. 지금 판매 가능 수량: 5", option.getId());

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("INV-18 · INV-19 증감량이 0이거나 사유와 부호가 맞지 않거나 쓸 수 없는 사유면 400으로 거절하고 아무것도 바꾸지 않는다")
    void rejectInvalidRequest(Map<String, Object> body, String detail) {
        expectProblem(adjust(tokens.seller(SELLER_ID), option.getId(), body),
            HttpStatus.BAD_REQUEST, "잘못된 요청입니다", detail, option.getId());

        assertThat(inventoryDeductionDb.availableQty(option)).isEqualTo(5);
        assertThat(sellerAdjustments(option)).isEmpty();
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            arguments(named("증감량 0", Map.of("deltaQuantity", 0, "reason", "CORRECTION")),
                "deltaQuantity: 증감량은 0이 아니어야 합니다."),
            arguments(named("입고에 음수", Map.of("deltaQuantity", -3, "reason", "RESTOCK")),
                "deltaQuantity: RESTOCK 사유는 증감량이 0보다 커야 합니다."),
            arguments(named("불량에 양수", Map.of("deltaQuantity", 3, "reason", "DEFECTIVE")),
                "deltaQuantity: DEFECTIVE 사유는 증감량이 0보다 작아야 합니다."),
            arguments(named("INITIAL 사유", Map.of("deltaQuantity", 3, "reason", "INITIAL")),
                "reason: 재고 조정에 쓸 수 없는 사유입니다: INITIAL"),
            arguments(named("정해지지 않은 사유", Map.of("deltaQuantity", 3, "reason", "GIFT")),
                "reason: 재고 조정에 쓸 수 없는 사유입니다: GIFT"),
            arguments(named("증감량 없음", Map.of("reason", "RESTOCK")), "deltaQuantity: 증감량을 넣어 주세요."),
            arguments(named("사유 없음", Map.of("deltaQuantity", 3)), "reason: 사유를 넣어 주세요."),
            arguments(named("증감량이 범위를 넘음", Map.of("deltaQuantity", 1_000_001, "reason", "RESTOCK")),
                "deltaQuantity: 증감량은 -1000000 이상 1000000 이하여야 합니다."));
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
            .headers(headers -> headers.setBearerAuth(token))
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
