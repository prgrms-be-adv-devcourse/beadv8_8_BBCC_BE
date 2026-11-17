package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductTypeService {

    private final ProductTypeRepository productTypeRepository;
    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductTypeResult create(ProductTypeCreateRequest request) {
        List<ProductType.LinkInfo> links = toLinkInfos(request.attributes());
        ProductType productType = productTypeRepository.save(ProductType.define(request.name(), links));
        return toResult(productType);
    }

    @Transactional
    public ProductTypeResult change(Long productTypeId, ProductTypeChangeRequest request) {
        var productType = productTypeRepository.getByIdForUpdate(productTypeId);
        List<ProductType.LinkInfo> links = toLinkInfos(request.attributes());
        productType.change(links, productRepository.existsByCategoryProductType(productType));
        return toResult(productType);
    }

    @Transactional(readOnly = true)
    public List<ProductTypeResult> findAll() {
        var results = new ArrayList<ProductTypeResult>();
        for (ProductType productType : productTypeRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            results.add(toResult(productType));
        }
        return results;
    }

    private List<ProductType.LinkInfo> toLinkInfos(List<ProductTypeCreateRequest.Link> requestLinks) {
        var links = new ArrayList<ProductType.LinkInfo>();
        for (ProductTypeCreateRequest.Link link : requestLinks) {
            links.add(toLinkInfo(link));
        }
        return links;
    }

    private ProductType.LinkInfo toLinkInfo(ProductTypeCreateRequest.Link link) {
        var attribute = attributeRepository.getByIdForShare(link.attributeId());
        var allowedValues = new ArrayList<AttributeValue>();
        for (Long valueId : link.allowedValueIds()) {
            allowedValues.add(attributeValueRepository.getById(valueId));
        }
        return new ProductType.LinkInfo(
            attribute,
            AttributeRole.valueOf(link.role()),
            link.required(),
            link.allValuesAllowed(),
            link.sizeRole(),
            link.priceAffecting(),
            allowedValues);
    }

    private ProductTypeResult toResult(ProductType productType) {
        var links = new ArrayList<ProductTypeResult.Link>();
        for (ProductTypeAttribute link : productType.getAttributes()) {
            Attribute attribute = link.getAttribute();
            links.add(new ProductTypeResult.Link(
                attribute.getId(),
                attribute.getCode(),
                attribute.getName(),
                attribute.getInputType().name(),
                link.getRole().name(),
                link.isRequired(),
                link.isAllValuesAllowed(),
                link.isSizeRole(),
                link.isPriceAffecting(),
                allowedValuesOf(productType, attribute)));
        }
        return new ProductTypeResult(productType.getId(), productType.getName(), links);
    }

    private List<AttributeResult.Value> allowedValuesOf(ProductType productType, Attribute attribute) {
        var values = new ArrayList<AttributeValue>();
        for (ProductTypeAllowedValue allowed : productType.getAllowedValues()) {
            AttributeValue value = allowed.getAttributeValue();
            if (value.getAttribute().getId().equals(attribute.getId())) {
                values.add(value);
            }
        }
        values.sort(Comparator.comparing(AttributeValue::getId));
        var results = new ArrayList<AttributeResult.Value>();
        for (AttributeValue value : values) {
            results.add(new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode()));
        }
        return results;
    }

    @Transactional
    public void delete(Long productTypeId) {
        var productType = productTypeRepository.getByIdForUpdate(productTypeId);
        boolean inUse = categoryRepository.existsByProductTypeAndDeletedAtIsNull(productType);
        productType.delete(LocalDateTime.now(), inUse);
    }
}
