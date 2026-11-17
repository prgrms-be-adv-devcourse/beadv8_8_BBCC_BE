package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

@FunctionalTest
class InventoryAdjustmentSoldOutApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ON_SALE", "STOPPED"})
    @DisplayName("SALE-08 판매를 시작한 상품에서 옵션의 마지막 재고를 줄이면 상품 정보 변경됨을 한 번 보내고, 그 옵션만 품절이다")
    void sendProductChangedWhenOptionSellsOut(String productStatus) {
        OnSaleOptions options = saveOptionsWithStock(5, productStatus);

        adjust(options.navy110(), -5, "LOST").expectStatus().isOk();

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), true),
                    tuple(options.navy120().getId(), false)));
    }

    @Test
    @DisplayName("SALE-08 품절이던 옵션에 재고를 더하면 상품 정보 변경됨을 한 번 보내고, 그 옵션은 품절이 아니다")
    void sendProductChangedWhenOptionBackInStock() {
        OnSaleOptions options = saveOptionsWithStock(0, "ON_SALE");

        adjust(options.navy110(), 10, "RESTOCK").expectStatus().isOk();

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), false),
                    tuple(options.navy120().getId(), false)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"RESTOCK, 3", "LOST, -1"})
    @DisplayName("SALE-08 판매 가능 수량 5인 옵션을 조정한 뒤에도 품절이 아니면 상품 정보 변경됨을 보내지 않는다")
    void skipProductChangedWhenSoldOutUnchanged(String reason, int deltaQuantity) {
        OnSaleOptions options = saveOptionsWithStock(5, "ON_SALE");

        adjust(options.navy110(), deltaQuantity, reason).expectStatus().isOk();

        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest(name = "판매 가능 수량 {0}에 {1} {2}")
    @CsvSource({"5, LOST, -5", "0, RESTOCK, 10"})
    @DisplayName("SALE-08 판매를 시작하기 전인 상품은 조정으로 품절 여부가 바뀌어도 상품 정보 변경됨을 보내지 않는다")
    void skipProductChangedBeforeSaleStarts(int stock, String reason, int deltaQuantity) {
        OnSaleOptions options = saveOptionsWithStock(stock, "SALE_PENDING");

        adjust(options.navy110(), deltaQuantity, reason).expectStatus().isOk();

        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("INV-03 판매 가능 수량 5인 옵션을 6 줄여 409로 거절된 조정은 상품 정보 변경됨을 보내지 않는다")
    void skipProductChangedWhenAdjustmentRejected() {
        OnSaleOptions options = saveOptionsWithStock(5, "ON_SALE");

        adjust(options.navy110(), -6, "LOST").expectStatus().isEqualTo(HttpStatus.CONFLICT);

        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    private OnSaleOptions saveOptionsWithStock(int navy110Stock, String productStatus) {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(navy110Stock, 10);
        jdbcTemplate.update("update product.product set status = ?"
            + " where id = (select product_id from product.product_option where id = ?)",
            productStatus, options.navy110().getId());
        eventRecorder.clear();
        return options;
    }

    private ResponseSpec adjust(ProductOption option, int deltaQuantity, String reason) {
        return client.post().uri("/api/v1/seller/options/" + option.getId() + "/inventory-adjustments")
            .headers(TestMembers.seller(SELLER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("deltaQuantity", deltaQuantity, "reason", reason))
            .exchange();
    }
}
