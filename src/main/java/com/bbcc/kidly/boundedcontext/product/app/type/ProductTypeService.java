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
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
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

    // 항목과 허용 값을 읽어 유형을 만든다. 유형 규칙의 검사는 도메인이 한다
    @Transactional
    public ProductTypeResult create(ProductTypeCreateRequest request) {
        List<ProductType.LinkInfo> links = toLinkInfos(request.attributes());
        ProductType productType = productTypeRepository.save(ProductType.define(request.name(), links));
        return toResult(productType);
    }

    // 상품 유형의 연결을 받은 목록으로 바꾼다. 받을 수 있는 변경인지는 유형이 판단한다
    @Transactional
    public ProductTypeResult change(Long productTypeId, ProductTypeChangeRequest request) {
        ProductType productType = productTypeRepository.getById(productTypeId);
        List<ProductType.LinkInfo> links = toLinkInfos(request.attributes());
        productType.change(links, productRepository.existsByCategoryProductType(productType));
        return toResult(productType);
    }

    // 모든 상품 유형을 ID 순서로 돌려준다
    @Transactional(readOnly = true)
    public List<ProductTypeResult> findAll() {
        List<ProductTypeResult> results = new ArrayList<>();
        for (ProductType productType : productTypeRepository.findAll()) {
            results.add(toResult(productType));
        }
        return results;
    }

    private List<ProductType.LinkInfo> toLinkInfos(List<ProductTypeCreateRequest.Link> requestLinks) {
        List<ProductType.LinkInfo> links = new ArrayList<>();
        for (ProductTypeCreateRequest.Link link : requestLinks) {
            links.add(toLinkInfo(link));
        }
        return links;
    }

    private ProductType.LinkInfo toLinkInfo(ProductTypeCreateRequest.Link link) {
        Attribute attribute = attributeRepository.getById(link.attributeId());
        List<AttributeValue> allowedValues = new ArrayList<>();
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

    // 연결은 연결 순서대로, 허용 값은 그 연결의 항목 값만 값 ID 순서로 담는다
    private ProductTypeResult toResult(ProductType productType) {
        List<ProductTypeResult.Link> links = new ArrayList<>();
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
        List<AttributeValue> values = new ArrayList<>();
        for (ProductTypeAllowedValue allowed : productType.getAllowedValues()) {
            AttributeValue value = allowed.getAttributeValue();
            if (value.getAttribute().getId().equals(attribute.getId())) {
                values.add(value);
            }
        }
        values.sort(Comparator.comparing(AttributeValue::getId));
        List<AttributeResult.Value> results = new ArrayList<>();
        for (AttributeValue value : values) {
            results.add(new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode()));
        }
        return results;
    }
}
