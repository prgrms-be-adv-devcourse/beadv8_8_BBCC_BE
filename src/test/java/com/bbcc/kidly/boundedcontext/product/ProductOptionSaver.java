package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통합 테스트용: 재고 · 차감 기록이 가리킬 옵션을 상품과 함께 저장한다. 상품은 상품 리포지토리로 저장한다.
 * 항목 · 목록 값 · 실측 항목 · 부모 카테고리는 상품 저장에 cascade되지 않고 저장할 리포지토리도 없어서 EntityManager로 먼저 저장한다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class ProductOptionSaver {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    ProductOptionSaver(ProductRepository productRepository, InventoryRepository inventoryRepository,
        EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // 상품 A에 네이비 · 110 옵션을 추가해 저장하고 그 옵션을 돌려준다. 저장은 새 트랜잭션에서 하고 커밋한다
    ProductOption saveNavy110() {
        ClothingOptions clothing = clothingOptions();
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        transactionTemplate.executeWithoutResult(status -> {
            saveClothingType(clothing);
            saveCategory(info.category());
            productRepository.save(product);
        });
        return product.getOptions().get(0);
    }

    // 판매를 시작한 상품 A의 네이비 · 110과 네이비 · 120 옵션을 저장하고, 두 옵션의 재고를 주어진 수량으로 연다.
    // 두 사이즈의 필수 실측과 권장 키를 넣어야 판매를 시작할 수 있다. 저장은 새 트랜잭션에서 하고 커밋한다
    OnSaleOptions saveOnSaleNavy110And120(int navy110Stock, int navy120Stock) {
        ClothingOptions clothing = clothingOptions();
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null)));
        Product product = Product.register(info);
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), info.category(), clothing.type());
        product.startSale(ProductFixture.SELLER_ID, info.category(), clothing.type());
        OnSaleOptions options = new OnSaleOptions(product.getOptions().get(0), product.getOptions().get(1));
        transactionTemplate.executeWithoutResult(status -> {
            saveClothingType(clothing);
            saveCategory(info.category());
            productRepository.save(product);
            inventoryRepository.saveOpening(
                InventoryOpening.open(options.navy110(), navy110Stock, ProductFixture.SELLER_ID));
            inventoryRepository.saveOpening(
                InventoryOpening.open(options.navy120(), navy120Stock, ProductFixture.SELLER_ID));
        });
        return options;
    }

    record OnSaleOptions(ProductOption navy110, ProductOption navy120) {
    }

    private void saveClothingType(ClothingOptions clothing) {
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
}
