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

@Component
class ReferenceDataLockSaver {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    ReferenceDataLockSaver(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    ProductType saveOtherType(SavedClothingTop saved) {
        return transactionTemplate.execute(status -> {
            Attribute color = entityManager.find(Attribute.class, saved.clothing().navy().getAttribute().getId());
            ProductType otherType = ProductType.define("다른 유형", List.of(ProductTypeFixture.requiredOption(color)));
            entityManager.persist(otherType);
            return otherType;
        });
    }

    Category saveOuter(SavedClothingTop saved) {
        return saveOuter(saved, saved.clothing().type().getId());
    }

    Category saveOuter(SavedClothingTop saved, Long productTypeId) {
        return transactionTemplate.execute(status -> {
            Category top = entityManager.find(Category.class, saved.top().getId());
            ProductType type = entityManager.find(ProductType.class, productTypeId);
            List<MeasurementItem> items = new ArrayList<>();
            for (CategoryMeasurementItem link : top.getMeasurementItems()) {
                items.add(link.getMeasurementItem());
            }
            Category outer = Category.createLeaf(new Category.LeafInfo("아우터", top.getParent(), type, items));
            entityManager.persist(outer);
            return outer;
        });
    }

    Attribute saveSeason() {
        return save(ProductTypeFixture.season());
    }

    <T> T save(T entity) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(entity));
        return entity;
    }

    ProductTypeChangeRequest linksPlus(Long productTypeId, ProductTypeCreateRequest.Link extra) {
        List<ProductTypeCreateRequest.Link> links = linksOf(productTypeId);
        links.add(extra);
        return new ProductTypeChangeRequest(links);
    }

    List<ProductTypeCreateRequest.Link> linksOf(Long productTypeId) {
        return transactionTemplate.execute(status -> {
            ProductType type = entityManager.find(ProductType.class, productTypeId);
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
            return links;
        });
    }

    static ProductTypeCreateRequest.Link infoLink(Attribute attribute, boolean required) {
        return new ProductTypeCreateRequest.Link(attribute.getId(), "INFO", required, true, false, false, List.of());
    }

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
