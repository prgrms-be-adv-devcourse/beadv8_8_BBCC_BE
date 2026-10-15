package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 주문 생성됨을 받아 재고를 차감하는 서비스를 실제 PostgreSQL로 확인한다. 리스너 대신 서비스를 직접 부른다.
 * 서비스가 트랜잭션을 직접 열고 커밋하므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryDeductionServiceIntegrationTest {

    private static final long ORDER_ID = 100L;
    private static final long MEMBER_ID = 1L;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("INV-04 재고가 충분하면 옵션별 판매 가능 수량을 줄이고 주문상품마다 DEDUCTED 기록을 남긴다. 같은 옵션의 두 줄은 합만큼 준다")
    void deductWhenAvailable() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            item(11L, options.navy110(), 2),
            item(12L, options.navy110(), 3),
            item(13L, options.navy120(), 1)));

        assertThat(availableQty(options.navy110())).isEqualTo(5);
        assertThat(availableQty(options.navy120())).isEqualTo(4);
        assertThat(records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, options.navy110().getId(), 3, "DEDUCTED"),
            tuple(ORDER_ID, 13L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-11 한 옵션이라도 판매 가능 수량이 부족하면 먼저 줄인 옵션까지 모두 되돌리고 주문상품마다 FAILED 기록을 남긴다")
    void rejectWhenAnyOptionIsShort() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 1);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 2)));

        assertThat(availableQty(options.navy110())).isEqualTo(10);
        assertThat(availableQty(options.navy120())).isEqualTo(1);
        assertThat(records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 2, "FAILED"));
    }

    private static OrderCreatedEvent orderCreated(long orderId, OrderCreatedEvent.Item... items) {
        return new OrderCreatedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCreatedEvent.Data(orderId, MEMBER_ID, List.of(items)));
    }

    private static OrderCreatedEvent.Item item(long orderItemId, ProductOption option, int quantity) {
        return new OrderCreatedEvent.Item(orderItemId, option.getId(), quantity);
    }

    private int availableQty(ProductOption option) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, option.getId());
    }

    private List<Tuple> records() {
        return jdbcTemplate.query(
            "select order_id, order_item_id, product_option_id, qty, status from product.inventory_deduction",
            (rs, rowNum) -> tuple(rs.getLong("order_id"), rs.getLong("order_item_id"),
                rs.getLong("product_option_id"), rs.getInt("qty"), rs.getString("status")));
    }
}
