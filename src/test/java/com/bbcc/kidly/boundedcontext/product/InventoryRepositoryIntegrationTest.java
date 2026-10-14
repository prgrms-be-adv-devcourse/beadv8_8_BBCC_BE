package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 리포지토리를 실제 DB로 확인한다. 리포지토리는 서비스 트랜잭션 안에서 불리므로, 테스트도 호출을 트랜잭션으로 감싼다.
 * 상품 쪽 리포지토리가 아직 없어서 재고를 열 옵션은 EntityManager로 직접 저장한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);

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

    @Test
    @DisplayName("옵션 하나에 재고 행은 하나라서, 같은 옵션의 재고를 두 번 열면 두 번째는 저장되지 않는다")
    void rejectSecondOpeningForSameOption() {
        ProductOption option = savedOption();
        inTransaction(() -> inventoryRepository.saveOpening(InventoryOpening.open(option, 10, SELLER_ID)));

        assertThatThrownBy(() -> inTransaction(
            () -> inventoryRepository.saveOpening(InventoryOpening.open(option, 5, SELLER_ID))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(availableQty(option)).isEqualTo(10);
    }

    @ParameterizedTest(name = "판매 가능 수량 {0}에서 {1}개 차감 → {2}")
    @CsvSource({"10, 1, 9", "10, 10, 0"})
    @DisplayName("INV-01 판매 가능 수량이 요청 수량 이상이면 줄이고 성공을 돌려준다")
    void deductWhenAvailable(int stock, int qty, int expectedQty) {
        ProductOption option = openedOption(stock);

        boolean deducted = inTransaction(() -> inventoryRepository.deductIfAvailable(option.getId(), qty, NOW));

        assertThat(deducted).isTrue();
        assertThat(availableQty(option)).isEqualTo(expectedQty);
    }

    @ParameterizedTest(name = "판매 가능 수량 {0}에서 {1}개 차감")
    @CsvSource({"10, 11", "0, 1"})
    @DisplayName("INV-01 판매 가능 수량보다 많이 요청하면 줄이지 않고 실패를 돌려준다")
    void rejectDeductionOverAvailable(int stock, int qty) {
        ProductOption option = openedOption(stock);

        boolean deducted = inTransaction(() -> inventoryRepository.deductIfAvailable(option.getId(), qty, NOW));

        assertThat(deducted).isFalse();
        assertThat(availableQty(option)).isEqualTo(stock);
    }

    @Test
    @DisplayName("INV-01 판매 가능 수량 10인 옵션에 1개씩 차감하는 요청 100건이 동시에 오면 10건만 성공하고 수량은 0이다")
    void deductConcurrently() throws Exception {
        ProductOption option = openedOption(10);
        Callable<Boolean> deductOne =
            () -> inTransaction(() -> inventoryRepository.deductIfAvailable(option.getId(), 1, NOW));
        ExecutorService executor = Executors.newFixedThreadPool(16);

        List<Future<Boolean>> results = executor.invokeAll(Collections.nCopies(100, deductOne));
        executor.shutdown();

        long succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        assertThat(succeeded).isEqualTo(10);
        assertThat(availableQty(option)).isZero();
    }

    // 판매 가능 수량 stock으로 재고를 연 옵션을 돌려준다
    private ProductOption openedOption(int stock) {
        ProductOption option = savedOption();
        inTransaction(() -> inventoryRepository.saveOpening(InventoryOpening.open(option, stock, SELLER_ID)));
        return option;
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

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private int availableQty(ProductOption option) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, option.getId());
    }
}
