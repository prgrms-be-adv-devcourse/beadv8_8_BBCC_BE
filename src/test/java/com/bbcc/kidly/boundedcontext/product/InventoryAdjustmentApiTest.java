package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    private ResponseSpec adjust(String token, long optionId, Map<String, Object> body) {
        return client.post().uri("/api/v1/seller/options/{optionId}/inventory-adjustments", optionId)
            .headers(headers -> headers.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
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
