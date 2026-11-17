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
import com.bbcc.kidly.shared.childprofile.domain.Gender;
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

    @Transactional
    public CategoryResult create(CategoryCreateRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.getByIdForShare(request.parentId());
        }
        // 최상위 카테고리끼리의 이름 중복은 DB UNIQUE가 막지 못하므로 먼저 찾아 본다. 같은 부모 아래 중복은 UNIQUE가 409로 막는다
        if (parent == null && categoryRepository.findTopLevelByName(request.name()).isPresent()) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        Category category = newCategory(request, parent);
        category.changeVisibleGender(request.visibleGender());
        category = categoryRepository.save(category);
        return toResult(category);
    }

    @Transactional
    public CategoryResult change(Long categoryId, CategoryChangeRequest request) {
        var category = categoryRepository.getByIdForUpdate(categoryId);
        var productType = productTypeRepository.getByIdForShare(request.productTypeId());
        List<MeasurementItem> measurementItems = findMeasurementItems(request.measurementItemIds());
        category.change(productType, measurementItems, productRepository.existsByCategory(category));
        if (request.visibleGender() != null) {
            category.changeVisibleGender(request.visibleGender());
        }
        return toResult(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResult> findAll() {
        var results = new ArrayList<CategoryResult>();
        for (Category category : categoryRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            results.add(toResult(category));
        }
        return results;
    }

    private Category newCategory(CategoryCreateRequest request, Category parent) {
        if (request.productTypeId() == null) {
            return Category.createMiddle(request.name(), parent);
        }
        var productType = productTypeRepository.getByIdForShare(request.productTypeId());
        List<MeasurementItem> measurementItems = findMeasurementItems(request.measurementItemIds());
        return Category.createLeaf(new Category.LeafInfo(request.name(), parent, productType, measurementItems));
    }

    private List<MeasurementItem> findMeasurementItems(List<Long> measurementItemIds) {
        var measurementItems = new ArrayList<MeasurementItem>();
        for (Long measurementItemId : measurementItemIds) {
            measurementItems.add(measurementItemRepository.getByIdForShare(measurementItemId));
        }
        return measurementItems;
    }

    private CategoryResult toResult(Category category) {
        Long parentId = null;
        if (category.getParent() != null) {
            parentId = category.getParent().getId();
        }
        Long productTypeId = null;
        String productTypeName = null;
        if (category.isLeaf()) {
            productTypeId = category.getProductType().getId();
            productTypeName = category.getProductType().getName();
        }
        var measurementItems = new ArrayList<MeasurementItemResult>();
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
            category.getVisibleGender(),
            measurementItems);
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeResult> findTree(Gender childGender) {
        var topLevel = new ArrayList<Category>();
        var childrenByParentId = new HashMap<Long, List<Category>>();
        for (Category category : categoryRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            if (!category.isVisibleTo(childGender)) {
                continue;
            }
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
        var results = new ArrayList<CategoryTreeResult>();
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

    @Transactional(readOnly = true)
    public CategoryInputsResult findInputs(Long categoryId) {
        var category = categoryRepository.getById(categoryId);
        ProductType type = category.productTypeToRegister();
        List<ProductTypeAttribute> links = type.inputAttributes();
        Map<Long, List<AttributeValue>> valuesByAttributeId = selectableValuesByAttributeId(type, links);
        var attributes = new ArrayList<CategoryInputsResult.InputAttribute>();
        for (ProductTypeAttribute link : links) {
            attributes.add(toInputAttribute(link, valuesByAttributeId));
        }
        var measurements = new ArrayList<CategoryInputsResult.Measurement>();
        for (CategoryMeasurementItem link : category.getMeasurementItems()) {
            MeasurementItem item = link.getMeasurementItem();
            measurements.add(new CategoryInputsResult.Measurement(item.getCode(), item.getName(), item.getUnit()));
        }
        return new CategoryInputsResult(
            category.getId(), category.getName(), type.getId(), type.getName(), attributes, measurements);
    }

    private Map<Long, List<AttributeValue>> selectableValuesByAttributeId(
        ProductType type, List<ProductTypeAttribute> links) {
        List<Attribute> attributes = links.stream().map(ProductTypeAttribute::getAttribute).toList();
        List<AttributeValue> values = attributeValueRepository.findAllOfAttributes(attributes);
        List<AttributeValue> selectable = type.selectableValues(values);
        var valuesByAttributeId = new HashMap<Long, List<AttributeValue>>();
        for (AttributeValue value : selectable) {
            valuesByAttributeId.computeIfAbsent(value.getAttribute().getId(), id -> new ArrayList<>()).add(value);
        }
        return valuesByAttributeId;
    }

    private CategoryInputsResult.InputAttribute toInputAttribute(
        ProductTypeAttribute link, Map<Long, List<AttributeValue>> valuesByAttributeId) {
        Attribute attribute = link.getAttribute();
        var values = new ArrayList<CategoryInputsResult.SelectableValue>();
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

    @Transactional
    public void delete(Long categoryId) {
        var category = categoryRepository.getByIdForUpdate(categoryId);
        boolean inUse = categoryRepository.existsByParentAndDeletedAtIsNull(category);
        category.delete(LocalDateTime.now(), inUse);
    }
}
