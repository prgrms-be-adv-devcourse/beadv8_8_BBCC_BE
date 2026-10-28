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

/**
 * 기능 테스트용: 상품을 등록할 의류 상품 유형과 의류 > 상의 카테고리를 저장한다.
 * 항목 · 목록 값 · 상품 유형 · 실측 항목 · 카테고리를 저장할 리포지토리가 없어서 EntityManager로 저장한다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class ClothingTopSaver {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    ClothingTopSaver(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 요청에 넣을 ID를 꺼낼 수 있게 저장한 의류 유형의 값들과 상의 카테고리다.
     */
    record SavedClothingTop(ClothingOptions clothing, Category top, Category clothingCategory) {
    }

    // 새 트랜잭션에서 저장하고 커밋한다
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
