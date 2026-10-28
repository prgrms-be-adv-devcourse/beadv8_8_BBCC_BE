package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.TwoOnSaleProducts;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 품절 전환 때 옵션이 속한 상품 행을 잠그는 쿼리를 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductLockQueryIntegrationTest {

    // PostgreSQL이 NOWAIT로 잠금을 얻지 못했을 때 돌려주는 SQLState다
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("옵션 ID를 어떤 순서로 넘겨도, 그 옵션들이 속한 상품을 한 번씩 ID 오름차순으로 돌려준다")
    void returnsEachProductOnceInIdOrder() {
        TwoOnSaleProducts products = productOptionSaver.saveTwoOnSaleProducts(1, 1);
        List<Long> optionIds = List.of(products.secondNavy110().getId(), products.firstNavy120().getId(),
            products.firstNavy110().getId());

        List<Long> productIds = new TransactionTemplate(transactionManager).execute(status ->
            productRepository.lockAllHavingOptions(optionIds).stream().map(Product::getId).toList());

        assertThat(productIds).containsExactly(products.firstId(), products.secondId());
    }

    @Test
    @DisplayName("잠근 트랜잭션이 끝나기 전에는 다른 연결이 그 상품 행을 잠그지 못하고, 넘기지 않은 상품 행은 잠글 수 있다")
    void otherConnectionCannotLockLockedProductUntilCommit() {
        TwoOnSaleProducts products = productOptionSaver.saveTwoOnSaleProducts(1, 1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            productRepository.lockAllHavingOptions(List.of(products.firstNavy110().getId()));

            assertThatThrownBy(() -> lockFromOtherConnection(products.firstId()))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                    exception -> assertThat(exception.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
            assertThatCode(() -> lockFromOtherConnection(products.secondId())).doesNotThrowAnyException();
        });
    }

    // 테스트 스레드의 트랜잭션과 다른 연결을 쓰도록, 다른 스레드에서 기다리지 않고(NOWAIT) 상품 행을 잠가 본다.
    // 그 스레드는 트랜잭션이 없어 잠금이 문장 하나가 끝나면 바로 풀린다
    private void lockFromOtherConnection(long productId) throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> jdbcTemplate.queryForObject(
                "select id from product.product where id = ? for no key update nowait", Long.class, productId))
                .get(10, TimeUnit.SECONDS);
        }
    }
}
