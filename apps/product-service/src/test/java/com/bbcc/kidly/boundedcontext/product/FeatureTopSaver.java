package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기능 테스트용: 의류 > 상의 옆에 특징(다중 선택)과 혼용률(직접 입력) 정보 항목이 있는 유형의 말단 카테고리를 저장한다.
 * 색상 · 사이즈 · 주요소재 항목과 실측 항목은 {@link ClothingTopSaver}가 저장한 것을 쓴다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class FeatureTopSaver {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    FeatureTopSaver(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 요청에 넣을 ID를 꺼낼 수 있게 저장한 말단 카테고리와 특징 · 혼용률 항목, 특징의 값이다.
     */
    record FeatureTop(
        Category category, Attribute feature, AttributeValue brushed, AttributeValue stretch, Attribute composition) {
    }

    // 새 트랜잭션에서 저장하고 커밋한다. 이미 저장한 항목 · 카테고리 · 실측 항목은 ID로 참조만 한다
    FeatureTop save(SavedClothingTop saved) {
        return transactionTemplate.execute(status -> {
            ClothingOptions clothing = saved.clothing();
            Attribute feature = Attribute.create("특징", "FEATURE", AttributeInputType.MULTI_LIST);
            Attribute composition = ProductTypeFixture.fabricComposition();
            AttributeValue brushed = AttributeValue.create(feature, "기모", null);
            AttributeValue stretch = AttributeValue.create(feature, "신축성", null);
            ProductType type = ProductType.define("특징 의류", List.of(
                ProductTypeFixture.requiredOption(reference(clothing.navy().getAttribute())),
                ProductTypeFixture.sizeOption(reference(clothing.size110().getAttribute())),
                ProductTypeFixture.requiredInfo(reference(clothing.mainMaterial())),
                ProductTypeFixture.optionalTextInfo(composition),
                new ProductType.LinkInfo(feature, AttributeRole.INFO, false, true, false, false, List.of())));
            List<MeasurementItem> measurementItems = new ArrayList<>();
            for (CategoryMeasurementItem link : saved.top().getMeasurementItems()) {
                measurementItems.add(
                    entityManager.getReference(MeasurementItem.class, link.getMeasurementItem().getId()));
            }
            Category category = Category.createLeaf(new Category.LeafInfo("특징 상의",
                entityManager.getReference(Category.class, saved.clothingCategory().getId()), type,
                measurementItems));
            Stream.of(feature, composition, brushed, stretch, type, category).forEach(entityManager::persist);
            return new FeatureTop(category, feature, brushed, stretch, composition);
        });
    }

    private Attribute reference(Attribute attribute) {
        return entityManager.getReference(Attribute.class, attribute.getId());
    }
}
