package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import jakarta.persistence.EntityManager;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class ClothingTopSaver {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    ClothingTopSaver(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    record SavedClothingTop(ClothingOptions clothing, Category top, Category clothingCategory) {
    }

    SavedClothingTop save() {
        ClothingOptions clothing = clothingOptions();
        Category clothingCategory = CategoryFixture.clothing();
        Category top = CategoryFixture.top(clothingCategory, clothing.type());
        transactionTemplate.executeWithoutResult(status -> {
            Stream.of(clothing.navy(), clothing.size110(), clothing.stripe(), clothing.cotton())
                .map(AttributeValue::getAttribute)
                .forEach(entityManager::persist);
            Stream.of(clothing.cotton(), clothing.navy(), clothing.oatmeal(), clothing.size110(), clothing.size120(),
                    clothing.sizeFree(), clothing.size105(), clothing.stripe())
                .forEach(entityManager::persist);
            entityManager.persist(clothing.type());
            entityManager.persist(clothingCategory);
            top.getMeasurementItems().forEach(link -> entityManager.persist(link.getMeasurementItem()));
            entityManager.persist(top);
        });
        return new SavedClothingTop(clothing, top, clothingCategory);
    }
}
