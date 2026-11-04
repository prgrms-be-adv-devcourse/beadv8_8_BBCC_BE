package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 정보 변경됨에 담을 상품 정보 값을, 실제 PostgreSQL에 저장했다가 새 트랜잭션에서 다시 읽은 상품으로 확인한다.
 * 항목과 목록 값은 지연 로딩이고 직접 입력 행은 목록 값 칸이 비어 있어서, 메모리 객체로 만든 단위 테스트와 따로 확인한다.
 * 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductChangedDataIntegrationTest {

    private static final String FABRIC_COMPOSITION = "면 95%, 폴리우레탄 5%";

    private final ProductFixture.Clothing clothing = ProductFixture.clothing();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SALE-08 다시 읽은 상품의 직접 입력 값은 값 ID 없이 입력한 글로, 다중 선택 값은 값마다 하나씩 담는다")
    void carryTextAndMultiListInfoValuesOfReloadedProduct() {
        Long productId = savedProductWithInfoValues();

        ProductChangedEvent.Data data = new TransactionTemplate(transactionManager).execute(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            ProductType type = entityManager.find(ProductType.class, clothing.type().getId());
            return ProductChangedData.of(product, type, Map.of());
        });

        assertThat(data.infoValues()).containsExactlyInAnyOrder(
            new ProductChangedEvent.AttributeEntry("MAIN_MATERIAL", clothing.cotton().getId(), "면"),
            new ProductChangedEvent.AttributeEntry("FABRIC_COMPOSITION", null, FABRIC_COMPOSITION),
            new ProductChangedEvent.AttributeEntry("FEATURE", clothing.brushed().getId(), "기모"),
            new ProductChangedEvent.AttributeEntry("FEATURE", clothing.stretch().getId(), "신축성"));
    }

    // 주요소재 · 혼용률 · 특징을 넣은 상품 A를 저장하고 ID를 돌려준다.
    // 항목 · 목록 값 · 유형 · 카테고리 · 실측 항목은 cascade되지 않아 상품보다 먼저 저장한다
    private Long savedProductWithInfoValues() {
        Product.RegisterInfo info = ProductFixture.productAWith(clothing.type(), List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null),
            new Product.InfoValueInfo(clothing.fabricComposition(), List.of(), FABRIC_COMPOSITION),
            new Product.InfoValueInfo(clothing.feature(), List.of(clothing.brushed(), clothing.stretch()), null)));
        Product product = Product.register(info);
        Category category = info.category();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            clothing.type().getAttributes().forEach(link -> entityManager.persist(link.getAttribute()));
            Stream.of(clothing.cotton(), clothing.brushed(), clothing.stretch()).forEach(entityManager::persist);
            entityManager.persist(clothing.type());
            entityManager.persist(category.getParent());
            category.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
            entityManager.persist(category);
            productRepository.save(product);
        });
        return product.getId();
    }
}
