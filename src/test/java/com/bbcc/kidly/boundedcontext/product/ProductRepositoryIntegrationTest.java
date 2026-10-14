package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 리포지토리와 상품 애그리거트의 저장 동작을 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 * 항목 · 목록 값 · 상품 유형 · 카테고리 · 실측 항목은 리포지토리가 아직 없어 EntityManager로 저장한다.
 */
@IntegrationTest
class ProductRepositoryIntegrationTest {

    private final ClothingOptions clothing = clothingOptions();
    private Category category;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("옵션만 추가하고 저장해도 상품의 version이 올라, 같은 상품을 동시에 고치는 요청 하나를 막는다")
    void addingOptionsIncreasesVersion() {
        Long productId = savedProductA();
        long before = version(productId);

        inTransaction(() -> productRepository.findById(productId).orElseThrow().addOptions(
            addOptions(List.of(option(managed(clothing.navy()), managed(clothing.size110())))), managedType()));

        assertThat(version(productId)).isGreaterThan(before);
    }

    @Test
    @DisplayName("SIZE-03 실측만 추가하고 저장해도 상품의 version이 올라, 같은 사이즈의 실측이 동시에 두 번 저장되지 않는다")
    void addingSizeSpecsIncreasesVersion() {
        Long productId = savedProductAWithOption110();
        long before = version(productId);

        inTransaction(() -> productRepository.findById(productId).orElseThrow().addSizeSpecs(
            addSizeSpecs(List.of(spec(managed(clothing.size110()), 105, 115, top110Measurements()))),
            managedCategory(), managedType()));

        assertThat(version(productId)).isGreaterThan(before);
    }

    @Test
    @DisplayName("OPT-03 같은 상품에 같은 옵션을 두 요청이 동시에 추가하면 하나만 저장되고 나머지는 version 충돌로 실패한다")
    void concurrentSameOptionAdditionsSaveOnlyOne() throws Exception {
        Long productId = savedProductA();
        // 두 요청이 모두 옵션을 추가한 뒤에 커밋하도록 맞춰, 둘 다 같은 version의 상품을 고치게 한다
        CyclicBarrier bothAdded = new CyclicBarrier(2);
        Callable<Void> addNavy110 = () -> {
            inTransaction(() -> {
                productRepository.findById(productId).orElseThrow().addOptions(
                    addOptions(List.of(option(managed(clothing.navy()), managed(clothing.size110())))), managedType());
                await(bothAdded);
            });
            return null;
        };
        List<Future<Void>> results;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            results = executor.invokeAll(List.of(addNavy110, addNavy110));
        }

        assertThat(results).filteredOn(result -> result.state() == Future.State.FAILED)
            .singleElement()
            .satisfies(result -> assertThat(result.exceptionNow())
                .isInstanceOf(ObjectOptimisticLockingFailureException.class));
        assertThat(optionCount(productId)).isEqualTo(1);
    }

    // 상품 A를 저장하고 네이비 110 옵션을 붙인 뒤 ID를 돌려준다
    private Long savedProductAWithOption110() {
        Long productId = savedProductA();
        inTransaction(() -> productRepository.findById(productId).orElseThrow().addOptions(
            addOptions(List.of(option(managed(clothing.navy()), managed(clothing.size110())))), managedType()));
        return productId;
    }

    // 옵션 없는 상품 A를 저장하고 ID를 돌려준다. 항목 · 목록 값 · 유형 · 카테고리 · 실측 항목은 cascade되지 않아 먼저 저장한다
    private Long savedProductA() {
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        category = info.category();
        inTransaction(() -> {
            saveClothingType();
            saveCategory(info.category());
            productRepository.save(product);
        });
        return product.getId();
    }

    private void saveClothingType() {
        Stream.of(clothing.navy(), clothing.size110(), clothing.stripe(), clothing.cotton())
            .map(AttributeValue::getAttribute)
            .forEach(entityManager::persist);
        Stream.of(clothing.cotton(), clothing.navy(), clothing.oatmeal(), clothing.size110(), clothing.size120(),
                clothing.sizeFree(), clothing.size105(), clothing.stripe())
            .forEach(entityManager::persist);
        entityManager.persist(clothing.type());
    }

    private void saveCategory(Category category) {
        entityManager.persist(category.getParent());
        category.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
        entityManager.persist(category);
    }

    // 상품 유형과 목록 값은 객체 동일성으로 비교하므로, 상품을 읽은 트랜잭션에서 다시 읽은 객체를 넘긴다
    private ProductType managedType() {
        return entityManager.find(ProductType.class, clothing.type().getId());
    }

    private Category managedCategory() {
        return entityManager.find(Category.class, category.getId());
    }

    private AttributeValue managed(AttributeValue value) {
        return entityManager.find(AttributeValue.class, value.getId());
    }

    private long optionCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.product_option where product_id = ?", Long.class, productId);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
    }

    private long version(Long productId) {
        return jdbcTemplate.queryForObject("select version from product.product where id = ?", Long.class, productId);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
