package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 리포지토리를 실제 DB로 확인한다. 리포지토리는 서비스 트랜잭션 안에서 불리므로, 테스트도 호출을 트랜잭션으로 감싼다.
 * 상품 쪽 리포지토리가 아직 없어서 재고를 열 옵션은 EntityManager로 직접 저장한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryRepositoryIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("INV-19 재고를 열어 저장하면 그 옵션의 재고 행과 판매자가 남긴 INITIAL +10 기록이 저장된다")
    void saveOpening() {
        ProductOption option = savedOption();

        inTransaction(() -> inventoryRepository.saveOpening(InventoryOpening.open(option, 10, SELLER_ID)));

        assertThat(availableQty(option)).isEqualTo(10);
        assertThat(jdbcTemplate.query(
            "select reason, delta_qty, adjusted_by from product.inventory_adjustment_record where product_option_id = ?",
            (rs, rowNum) -> tuple(rs.getString("reason"), rs.getInt("delta_qty"), rs.getLong("adjusted_by")),
            option.getId()))
            .containsExactly(tuple("INITIAL", 10, SELLER_ID));
    }

    // 상품 A에 네이비 · 110 옵션을 추가해 저장하고 그 옵션을 돌려준다. 항목과 목록 값은 cascade되지 않아 먼저 저장한다
    private ProductOption savedOption() {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        inTransaction(() -> {
            Stream.of(clothing.navy(), clothing.size110(), clothing.stripe(), clothing.cotton())
                .map(value -> value.getAttribute())
                .forEach(entityManager::persist);
            Stream.of(clothing.cotton(), clothing.navy(), clothing.oatmeal(), clothing.size110(), clothing.size120(),
                    clothing.sizeFree(), clothing.size105(), clothing.stripe())
                .forEach(entityManager::persist);
            entityManager.persist(clothing.type());
            entityManager.persist(product);
        });
        return product.getOptions().get(0);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private int availableQty(ProductOption option) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, option.getId());
    }
}
