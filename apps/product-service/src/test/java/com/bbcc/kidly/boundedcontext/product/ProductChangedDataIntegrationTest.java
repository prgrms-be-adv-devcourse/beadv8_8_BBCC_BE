package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
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
            Category category = entityManager.find(Category.class, product.getCategoryId());
            return ProductChangedData.of(product, category, Map.of());
        });

        assertThat(data.infoValues()).containsExactlyInAnyOrder(
            new ProductChangedEvent.AttributeEntry("MAIN_MATERIAL", clothing.cotton().getId(), "면"),
            new ProductChangedEvent.AttributeEntry("FABRIC_COMPOSITION", null, FABRIC_COMPOSITION),
            new ProductChangedEvent.AttributeEntry("FEATURE", clothing.brushed().getId(), "기모"),
            new ProductChangedEvent.AttributeEntry("FEATURE", clothing.stretch().getId(), "신축성"));
    }

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
