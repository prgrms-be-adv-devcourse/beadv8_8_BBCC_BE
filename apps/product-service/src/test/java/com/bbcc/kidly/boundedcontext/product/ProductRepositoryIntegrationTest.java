package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    @Test
    @DisplayName("JSONB 칸인 이미지 주소 목록과 실측 값은 저장한 그대로 돌아오고, 실측 값은 소수까지 BigDecimal로 읽힌다")
    void jsonbColumnsRoundTrip() {
        Long productId = savedProductAWithOption110();
        Map<String, BigDecimal> measurements = new HashMap<>(top110Measurements());
        measurements.put("CHEST_WIDTH", new BigDecimal("34.5"));
        inTransaction(() -> productRepository.findById(productId).orElseThrow().addSizeSpecs(
            addSizeSpecs(List.of(spec(managed(clothing.size110()), 105, 115, measurements))),
            managedCategory(), managedType()));

        inTransaction(() -> {
            Product product = productRepository.findById(productId).orElseThrow();

            assertThat(product.getImageUrls()).containsExactly("https://…/a-1.jpg");
            assertThat(product.getSizeSpecs()).singleElement()
                .extracting(SizeSpec::getMeasurements)
                .satisfies(read -> {
                    assertThat(read).containsExactlyInAnyOrderEntriesOf(measurements);
                    assertThat(read.values()).allMatch(BigDecimal.class::isInstance);
                });
        });
    }

    @Test
    @DisplayName("옵션 · 실측은 추가한 순서로, 상품 유형의 항목은 연결한 순서로 다시 읽힌다")
    void collectionsAreReadInDeclaredOrder() {
        Long productId = savedProductA();
        inTransaction(() -> productRepository.findById(productId).orElseThrow().addOptions(addOptions(List.of(
            option(managed(clothing.navy()), managed(clothing.size110())),
            option(managed(clothing.navy()), managed(clothing.size120())))), managedType()));
        inTransaction(() -> productRepository.findById(productId).orElseThrow().addSizeSpecs(addSizeSpecs(List.of(
            spec(managed(clothing.size110()), 105, 115, top110Measurements()),
            spec(managed(clothing.size120()), 115, 125, top120Measurements()))), managedCategory(), managedType()));
        moveFirstRowToEnd("product.product_option", "product_id", productId);
        moveFirstRowToEnd("product.size_spec", "product_id", productId);
        moveFirstRowToEnd("product.product_type_attribute", "product_type_id", clothing.type().getId());

        inTransaction(() -> {
            Product product = productRepository.findById(productId).orElseThrow();

            assertThat(product.getOptions()).extracting(ProductOption::getName)
                .containsExactly("색상: 네이비 / 사이즈: 110", "색상: 네이비 / 사이즈: 120");
            assertThat(product.getSizeSpecs()).extracting(spec -> spec.getSizeValue().getName())
                .containsExactly("110", "120");
            assertThat(managedType().getAttributes()).extracting(link -> link.getAttribute().getCode())
                .containsExactly("COLOR", "SIZE", "PATTERN", "MAIN_MATERIAL");
        });
    }

    @Test
    @DisplayName("SALE-04 새로 읽은 옵션은 상품이 지연 로딩 프록시여도, 상품과 옵션이 판매중이면 살 수 있다")
    void purchasableWithProductProxy() {
        Long productId = savedProductAWithOption110();
        inTransaction(() -> {
            Product product = productRepository.findById(productId).orElseThrow();
            product.addSizeSpecs(
                addSizeSpecs(List.of(spec(managed(clothing.size110()), 105, 115, top110Measurements()))),
                managedCategory(), managedType());
            product.startSale(SELLER_ID, managedCategory(), managedType());
        });
        Long optionId = jdbcTemplate.queryForObject(
            "select id from product.product_option where product_id = ?", Long.class, productId);

        inTransaction(() -> {
            ProductOption option = entityManager.find(ProductOption.class, optionId);

            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(option, "product"))
                .isFalse();
            assertThat(option.isPurchasable()).isTrue();
        });
    }

    @Test
    @DisplayName("상품이 가리키는 카테고리는 ID만 읽으면 지연 로딩 프록시를 초기화하지 않는다")
    void categoryIdWithoutInitializingProxy() {
        Long productId = savedProductA();

        inTransaction(() -> {
            Product product = productRepository.findById(productId).orElseThrow();

            assertThat(product.getCategoryId()).isEqualTo(category.getId());
            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(product, "category"))
                .isFalse();
        });
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

    // 항목은 유형에 연결한 순서(색상 · 사이즈 · 무늬 · 주요소재)와 반대로 저장해, 항목 ID 순서가 연결 순서와 다르게 한다
    private void saveClothingType() {
        Stream.of(clothing.cotton(), clothing.stripe(), clothing.size110(), clothing.navy())
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

    // 먼저 넣은 행을 지우고 같은 값으로 다시 넣어 표 뒤쪽으로 옮긴다. 순서를 정하지 않고 읽으면 이 행이 맨 뒤에 나온다
    // id를 그대로 두는 UPDATE는 색인이 원래 자리를 가리켜, 색인으로 읽을 때 순서가 바뀌지 않는다
    private void moveFirstRowToEnd(String table, String ownerColumn, Long ownerId) {
        jdbcTemplate.update("with moved as (delete from " + table + " where id = (select min(id) from " + table
            + " where " + ownerColumn + " = ?) returning *) insert into " + table + " select * from moved", ownerId);
    }

    private long optionCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.product_option where product_id = ?", Long.class, productId);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
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
