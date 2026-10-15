package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 통합 테스트용: 재고 차감 결과를 DB에서 직접 읽고, 아직 기능이 없는 판매 중지 · 입고를 DB에서 직접 만든다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class InventoryDeductionDb {

    private final JdbcTemplate jdbcTemplate;

    InventoryDeductionDb(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    int availableQty(ProductOption option) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, option.getId());
    }

    // 차감 기록을 (주문 ID, 주문상품 ID, 옵션 ID, 수량, 상태)로 읽는다
    List<Tuple> records() {
        return jdbcTemplate.query(
            "select order_id, order_item_id, product_option_id, qty, status from product.inventory_deduction",
            (rs, rowNum) -> tuple(rs.getLong("order_id"), rs.getLong("order_item_id"),
                rs.getLong("product_option_id"), rs.getInt("qty"), rs.getString("status")));
    }

    long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.inventory_deduction where status = ?", Long.class, status);
    }

    // 판매 중지 기능이 아직 없어서, 옵션이 속한 상품의 판매 상태를 DB에서 직접 판매중지로 바꾼다
    void stopSale(ProductOption option) {
        jdbcTemplate.update("update product.product set status = 'STOPPED'"
            + " where id = (select product_id from product.product_option where id = ?)", option.getId());
    }

    // 재고 조정 기능이 아직 없어서, 입고를 DB에서 직접 만든다. 수량과 입고 기록을 함께 늘려 INV-02 식을 지킨다
    void restock(ProductOption option, int addedQty) {
        jdbcTemplate.update(
            "update product.inventory set available_qty = available_qty + ? where product_option_id = ?",
            addedQty, option.getId());
        jdbcTemplate.update("insert into product.inventory_adjustment_record"
            + " (product_option_id, delta_qty, reason, adjusted_by, created_at, updated_at)"
            + " values (?, ?, 'RESTOCK', ?, now(), now())", option.getId(), addedQty, ProductFixture.SELLER_ID);
    }

    /**
     * 차감이 끝난 뒤 재고 불변식을 모든 옵션에서 확인한다.
     * INV-02 판매 가능 수량 + DEDUCTED 수량 합 = 재고 조정 기록 증감량 합, INV-01 판매 가능 수량은 0 이상,
     * INV-10 주문상품마다 차감 기록은 한 줄이다.
     */
    void assertInvariants() {
        List<Tuple> stock = jdbcTemplate.query("""
            select i.product_option_id, i.available_qty,
                (select coalesce(sum(d.qty), 0) from product.inventory_deduction d
                 where d.product_option_id = i.product_option_id and d.status = 'DEDUCTED') as deducted_qty,
                (select coalesce(sum(a.delta_qty), 0) from product.inventory_adjustment_record a
                 where a.product_option_id = i.product_option_id) as adjusted_qty
            from product.inventory i
            """, (rs, rowNum) -> tuple(rs.getLong("product_option_id"), rs.getInt("available_qty"),
            rs.getInt("deducted_qty"), rs.getInt("adjusted_qty")));
        for (Tuple row : stock) {
            List<Object> values = row.toList();
            int available = (int) values.get(1);
            assertThat(available).as("INV-01 옵션 %s의 판매 가능 수량", values.get(0)).isNotNegative();
            assertThat(available + (int) values.get(2)).as("INV-02 옵션 %s", values.get(0)).isEqualTo(values.get(3));
        }
        assertThat(jdbcTemplate.queryForList("select order_item_id from product.inventory_deduction"
            + " group by order_item_id having count(*) > 1", Long.class)).as("INV-10 주문상품마다 기록 한 줄").isEmpty();
    }
}
