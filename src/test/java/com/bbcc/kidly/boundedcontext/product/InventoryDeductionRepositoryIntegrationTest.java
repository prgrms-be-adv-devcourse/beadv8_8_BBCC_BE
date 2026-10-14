package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 차감 기록 리포지토리를 실제 DB로 확인한다. 리포지토리는 서비스 트랜잭션 안에서 불리므로, 테스트도 호출을 트랜잭션으로 감싼다.
 * 상품 쪽 리포지토리가 아직 없어서 차감할 옵션은 EntityManager로 직접 저장한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryDeductionRepositoryIntegrationTest {

    private static final long ORDER_ID = 100L;
    private static final long OTHER_ORDER_ID = 200L;

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("INV-10 같은 주문 생성됨을 다시 받았는지 보려고 주문상품 ID 목록으로 찾으면, 그 주문상품의 기록만 저장한 그대로 나온다")
    void findByOrderItemIds() {
        ProductOption option = savedOption();
        inTransaction(() -> {
            inventoryDeductionRepository.saveAll(InventoryDeductionRequest.of(ORDER_ID, List.of(
                new InventoryDeductionRequest.Item(11L, option, 2),
                new InventoryDeductionRequest.Item(12L, option, 1))).deductedRecords());
            inventoryDeductionRepository.saveAll(InventoryDeductionRequest.of(OTHER_ORDER_ID, List.of(
                new InventoryDeductionRequest.Item(21L, option, 3))).failedRecords());
        });

        List<Tuple> found = inTransaction(() -> inventoryDeductionRepository.findAllByOrderItemIdIn(List.of(11L, 12L))
            .stream()
            .map(record -> tuple(record.getOrderId(), record.getOrderItemId(), record.getProductOptionId(),
                record.getQty(), record.getStatus().name()))
            .toList());

        assertThat(found).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, option.getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, option.getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-10 같은 주문상품의 차감 기록을 또 저장하면 order_item_id UNIQUE 제약이 막아 한 줄만 남는다")
    void rejectDuplicateOrderItem() {
        ProductOption option = savedOption();
        inTransaction(() -> inventoryDeductionRepository.saveAll(InventoryDeductionRequest.of(ORDER_ID, List.of(
            new InventoryDeductionRequest.Item(11L, option, 2))).deductedRecords()));

        assertThatThrownBy(() -> inTransaction(() -> inventoryDeductionRepository.saveAll(
            InventoryDeductionRequest.of(OTHER_ORDER_ID, List.of(
                new InventoryDeductionRequest.Item(11L, option, 1))).failedRecords())))
            .isInstanceOf(DataIntegrityViolationException.class);
        List<Long> orderIds = inTransaction(() -> inventoryDeductionRepository.findAllByOrderItemIdIn(List.of(11L))
            .stream()
            .map(InventoryDeduction::getOrderId)
            .toList());
        assertThat(orderIds).containsExactly(ORDER_ID);
    }

    @Test
    @DisplayName("트랜잭션 밖에서도 조회한 차감 기록의 옵션 ID를 옵션 프록시를 초기화하지 않고 읽는다")
    void readOptionIdOutsideTransaction() {
        ProductOption option = savedOption();
        inTransaction(() -> inventoryDeductionRepository.saveAll(InventoryDeductionRequest.of(ORDER_ID, List.of(
            new InventoryDeductionRequest.Item(11L, option, 2))).deductedRecords()));

        InventoryDeduction found = inTransaction(
            () -> inventoryDeductionRepository.findAllByOrderItemIdIn(List.of(11L)).get(0));

        assertThat(found.getProductOptionId()).isEqualTo(option.getId());
        assertThat(Hibernate.isInitialized(ReflectionTestUtils.getField(found, "productOption"))).isFalse();
    }

    // 상품 A에 네이비 · 110 옵션을 추가해 저장하고 그 옵션을 돌려준다. 항목 · 목록 값 · 카테고리 · 실측 항목은
    // cascade되지 않아 먼저 저장한다
    private ProductOption savedOption() {
        ClothingOptions clothing = clothingOptions();
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        inTransaction(() -> {
            Stream.of(clothing.navy(), clothing.size110(), clothing.stripe(), clothing.cotton())
                .map(value -> value.getAttribute())
                .forEach(entityManager::persist);
            Stream.of(clothing.cotton(), clothing.navy(), clothing.oatmeal(), clothing.size110(), clothing.size120(),
                    clothing.sizeFree(), clothing.size105(), clothing.stripe())
                .forEach(entityManager::persist);
            entityManager.persist(clothing.type());
            Category category = info.category();
            entityManager.persist(category.getParent());
            category.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
            entityManager.persist(category);
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
}
