package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통합 테스트용: 동시성 테스트가 {@link ClothingTopSaver}로 저장한 의류 > 상의 옆에 더 저장할 기준 데이터와 요청을 만든다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class ReferenceDataLockSaver {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    ReferenceDataLockSaver(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // 상의 카테고리의 의류 유형과 다른 유형을 저장한다. 색상만 필수 옵션 항목이라 의류 상품의 정보 값 · 사이즈와 맞지 않는다
    ProductType saveOtherType(SavedClothingTop saved) {
        return transactionTemplate.execute(status -> {
            Attribute color = entityManager.find(Attribute.class, saved.clothing().navy().getAttribute().getId());
            ProductType otherType = ProductType.define("다른 유형", List.of(ProductTypeFixture.requiredOption(color)));
            entityManager.persist(otherType);
            return otherType;
        });
    }

    // 상의와 같은 의류 유형 · 필수 실측을 쓰는 의류 > 아우터를 저장한다. 상품을 옮겨 갈 카테고리다
    Category saveOuter(SavedClothingTop saved) {
        return transactionTemplate.execute(status -> {
            Category top = entityManager.find(Category.class, saved.top().getId());
            List<MeasurementItem> items = new ArrayList<>();
            for (CategoryMeasurementItem link : top.getMeasurementItems()) {
                items.add(link.getMeasurementItem());
            }
            Category outer = Category.createLeaf(
                new Category.LeafInfo("아우터", top.getParent(), top.getProductType(), items));
            entityManager.persist(outer);
            return outer;
        });
    }

    // 정보 항목으로 연결할 「계절」 목록 항목을 저장한다
    Attribute saveSeason() {
        return save(ProductTypeFixture.season());
    }

    <T> T save(T entity) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(entity));
        return entity;
    }

    // 의류 유형의 지금 연결을 그대로 두고 연결 하나를 끝에 더하는 변경 요청이다
    ProductTypeChangeRequest linksPlus(SavedClothingTop saved, ProductTypeCreateRequest.Link extra) {
        return transactionTemplate.execute(status -> {
            ProductType type = entityManager.find(ProductType.class, saved.clothing().type().getId());
            List<ProductTypeCreateRequest.Link> links = new ArrayList<>();
            for (ProductTypeAttribute link : type.getAttributes()) {
                List<Long> allowedValueIds = new ArrayList<>();
                for (ProductTypeAllowedValue allowed : type.getAllowedValues()) {
                    if (allowed.getAttributeValue().getAttribute() == link.getAttribute()) {
                        allowedValueIds.add(allowed.getAttributeValue().getId());
                    }
                }
                links.add(new ProductTypeCreateRequest.Link(link.getAttribute().getId(), link.getRole().name(),
                    link.isRequired(), link.isAllValuesAllowed(), link.isSizeRole(), link.isPriceAffecting(),
                    allowedValueIds));
            }
            links.add(extra);
            return new ProductTypeChangeRequest(links);
        });
    }

    static ProductTypeCreateRequest.Link infoLink(Attribute attribute, boolean required) {
        return new ProductTypeCreateRequest.Link(attribute.getId(), "INFO", required, true, false, false, List.of());
    }

    // 의류 > 상의에 네이비 · 110 옵션 하나와 110 실측을 채운 판매대기 상품 등록 요청이다
    static ProductRegisterRequest registerRequest(SavedClothingTop saved) {
        Long size110 = saved.clothing().size110().getId();
        return new ProductRegisterRequest("데일리 맨투맨", "BBCC", saved.top().getId(), null, null, List.of(),
            List.of(new ProductRegisterRequest.AttributeInput(
                saved.clothing().mainMaterial().getId(), List.of(saved.clothing().cotton().getId()), null)),
            List.of(new ProductRegisterRequest.OptionInput(
                List.of(saved.clothing().navy().getId(), size110), 10, null)),
            List.of(new ProductRegisterRequest.PriceInput(List.of(), 25_000L)),
            List.of(new ProductRegisterRequest.SizeSpecInput(size110, 105, 115, Map.of(
                "TOTAL_LENGTH", BigDecimal.valueOf(42), "SHOULDER_WIDTH", BigDecimal.valueOf(30),
                "CHEST_WIDTH", BigDecimal.valueOf(34), "SLEEVE_LENGTH", BigDecimal.valueOf(38)))));
    }
}
