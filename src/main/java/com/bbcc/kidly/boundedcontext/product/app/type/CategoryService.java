package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductTypeRepository productTypeRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final ProductRepository productRepository;

    // 카테고리를 만들어 저장한다. 부모가 있으면 먼저 읽고, 상품 유형이 있으면 말단 카테고리로 만든다
    @Transactional
    public CategoryResult create(CategoryCreateRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.getById(request.parentId());
        }
        // 최상위 카테고리끼리의 이름 중복은 DB UNIQUE가 막지 못하므로 먼저 찾아 본다. 같은 부모 아래 중복은 UNIQUE가 409로 막는다
        if (parent == null && categoryRepository.findTopLevelByName(request.name()).isPresent()) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        Category category = categoryRepository.save(newCategory(request, parent));
        return toResult(category);
    }

    // 말단 카테고리의 상품 유형과 필수 실측 항목을 바꾼다. 받을 수 있는 변경인지는 카테고리가 판단한다
    @Transactional
    public CategoryResult change(Long categoryId, CategoryChangeRequest request) {
        Category category = categoryRepository.getById(categoryId);
        ProductType productType = productTypeRepository.getById(request.productTypeId());
        List<MeasurementItem> measurementItems = findMeasurementItems(request.measurementItemIds());
        category.change(productType, measurementItems, productRepository.existsByCategory(category));
        return toResult(category);
    }

    // 지우지 않은 모든 카테고리를 ID 순서로 평평하게 돌려준다
    @Transactional(readOnly = true)
    public List<CategoryResult> findAll() {
        List<CategoryResult> results = new ArrayList<>();
        for (Category category : categoryRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            results.add(toResult(category));
        }
        return results;
    }

    private Category newCategory(CategoryCreateRequest request, Category parent) {
        if (request.productTypeId() == null) {
            return Category.createMiddle(request.name(), parent);
        }
        ProductType productType = productTypeRepository.getById(request.productTypeId());
        List<MeasurementItem> measurementItems = findMeasurementItems(request.measurementItemIds());
        return Category.createLeaf(new Category.LeafInfo(request.name(), parent, productType, measurementItems));
    }

    private List<MeasurementItem> findMeasurementItems(List<Long> measurementItemIds) {
        List<MeasurementItem> measurementItems = new ArrayList<>();
        for (Long measurementItemId : measurementItemIds) {
            measurementItems.add(measurementItemRepository.getById(measurementItemId));
        }
        return measurementItems;
    }

    private CategoryResult toResult(Category category) {
        Long parentId = null;
        if (category.getParent() != null) {
            // 부모는 지연 로딩 프록시이므로 ID만 읽는다
            parentId = category.getParent().getId();
        }
        Long productTypeId = null;
        String productTypeName = null;
        if (category.isLeaf()) {
            productTypeId = category.getProductType().getId();
            productTypeName = category.getProductType().getName();
        }
        List<MeasurementItemResult> measurementItems = new ArrayList<>();
        for (CategoryMeasurementItem link : category.getMeasurementItems()) {
            MeasurementItem item = link.getMeasurementItem();
            measurementItems.add(new MeasurementItemResult(
                item.getId(), item.getName(), item.getCode(), item.getUnit()));
        }
        return new CategoryResult(
            category.getId(),
            category.getName(),
            parentId,
            category.isLeaf(),
            productTypeId,
            productTypeName,
            measurementItems);
    }

    // 지우지 않은 최상위 카테고리부터 하위 카테고리까지 계층으로 돌려준다. 같은 부모 아래에서는 ID 순서다
    @Transactional(readOnly = true)
    public List<CategoryTreeResult> findTree() {
        List<Category> topLevel = new ArrayList<>();
        Map<Long, List<Category>> childrenByParentId = new HashMap<>();
        for (Category category : categoryRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            // 부모는 지연 로딩 프록시이므로 ID만 읽는다. 프록시를 초기화하지 않아 쿼리가 늘지 않는다
            Category parent = category.getParent();
            if (parent == null) {
                topLevel.add(category);
                continue;
            }
            childrenByParentId.computeIfAbsent(parent.getId(), id -> new ArrayList<>()).add(category);
        }
        return toResults(topLevel, childrenByParentId);
    }

    private List<CategoryTreeResult> toResults(
        List<Category> categories, Map<Long, List<Category>> childrenByParentId) {
        List<CategoryTreeResult> results = new ArrayList<>();
        for (Category category : categories) {
            List<Category> children = childrenByParentId.getOrDefault(category.getId(), List.of());
            results.add(new CategoryTreeResult(
                category.getId(),
                category.getName(),
                category.isLeaf(),
                toResults(children, childrenByParentId)));
        }
        return results;
    }

    // 말단 카테고리의 상품 등록 입력 항목을 돌려준다. 중간 카테고리이면 도메인이 거절한다
    @Transactional(readOnly = true)
    public CategoryInputsResult findInputs(Long categoryId) {
        Category category = categoryRepository.getById(categoryId);
        ProductType type = category.productTypeToRegister();
        List<ProductTypeAttribute> links = type.inputAttributes();
        Map<Long, List<AttributeValue>> valuesByAttributeId = selectableValuesByAttributeId(type, links);
        List<CategoryInputsResult.InputAttribute> attributes = new ArrayList<>();
        for (ProductTypeAttribute link : links) {
            attributes.add(toInputAttribute(link, valuesByAttributeId));
        }
        List<CategoryInputsResult.Measurement> measurements = new ArrayList<>();
        for (CategoryMeasurementItem link : category.getMeasurementItems()) {
            MeasurementItem item = link.getMeasurementItem();
            measurements.add(new CategoryInputsResult.Measurement(item.getCode(), item.getName(), item.getUnit()));
        }
        return new CategoryInputsResult(
            category.getId(), category.getName(), type.getId(), type.getName(), attributes, measurements);
    }

    // 항목마다 유형에서 고를 수 있는 값을 ID 순서로 묶는다
    private Map<Long, List<AttributeValue>> selectableValuesByAttributeId(
        ProductType type, List<ProductTypeAttribute> links) {
        List<Attribute> attributes = new ArrayList<>();
        for (ProductTypeAttribute link : links) {
            attributes.add(link.getAttribute());
        }
        List<AttributeValue> values = attributeValueRepository.findAllOfAttributes(attributes);
        List<AttributeValue> selectable = type.selectableValues(values);
        Map<Long, List<AttributeValue>> valuesByAttributeId = new HashMap<>();
        for (AttributeValue value : selectable) {
            valuesByAttributeId.computeIfAbsent(value.getAttribute().getId(), id -> new ArrayList<>()).add(value);
        }
        return valuesByAttributeId;
    }

    private CategoryInputsResult.InputAttribute toInputAttribute(
        ProductTypeAttribute link, Map<Long, List<AttributeValue>> valuesByAttributeId) {
        Attribute attribute = link.getAttribute();
        List<CategoryInputsResult.SelectableValue> values = new ArrayList<>();
        for (AttributeValue value : valuesByAttributeId.getOrDefault(attribute.getId(), List.of())) {
            values.add(new CategoryInputsResult.SelectableValue(value.getId(), value.getName(), value.getColorCode()));
        }
        return new CategoryInputsResult.InputAttribute(
            attribute.getId(),
            attribute.getCode(),
            attribute.getName(),
            attribute.getInputType().name(),
            link.getRole().name(),
            link.isRequired(),
            link.isSizeRole(),
            link.isPriceAffecting(),
            values);
    }

    // 카테고리를 지운다. 지우지 않은 하위 카테고리가 있는지는 읽어 넘기고, 지울 수 있는지는 카테고리가 판단한다 (TYPE-10)
    @Transactional
    public void delete(Long categoryId) {
        Category category = categoryRepository.getById(categoryId);
        boolean inUse = categoryRepository.existsByParentAndDeletedAtIsNull(category);
        category.delete(LocalDateTime.now(), inUse);
    }
}
