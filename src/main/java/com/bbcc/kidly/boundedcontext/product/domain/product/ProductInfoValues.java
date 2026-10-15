package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.InfoValueInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 상품을 등록할 때 받은 정보 값을 검사하고 정보 값 행으로 만든다. Product.register만 쓴다.
 */
final class ProductInfoValues {

    private ProductInfoValues() {
    }

    // 정보 값의 항목과 값 목록, 목록 안의 값은 null이면 안 된다. 직접 입력한 글(text)은 null을 허용한다
    static void requireNonNullValues(List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            requireNonNull(infoValue, "infoValue는 필수입니다");
            requireNonNull(infoValue.attribute(), "attribute는 필수입니다");
            requireNonNull(infoValue.values(), "values는 필수입니다");
            for (AttributeValue value : infoValue.values()) {
                requireNonNull(value, "value는 필수입니다");
            }
        }
    }

    // 목록 값마다 한 행을, 직접 입력한 글이 있으면 그 항목의 한 행을 입력 순서대로 만든다
    static List<ProductInfoValue> create(Product product, List<InfoValueInfo> infoValues) {
        List<ProductInfoValue> rows = new ArrayList<>();
        for (InfoValueInfo infoValue : infoValues) {
            for (AttributeValue value : infoValue.values()) {
                rows.add(ProductInfoValue.create(product, infoValue.attribute(), value));
            }
            if (hasText(infoValue.text())) {
                rows.add(ProductInfoValue.createText(product, infoValue.attribute(), infoValue.text()));
            }
        }
        return rows;
    }

    // 받은 값으로 만들 행이 지금 행과 순서까지 모두 같은지 본다. 비교용 행은 create로 만들기만 하고 상품에 붙이지 않는다
    static boolean sameAs(Product product, List<ProductInfoValue> current, List<InfoValueInfo> infoValues) {
        List<ProductInfoValue> received = create(product, infoValues);
        if (received.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < received.size(); i++) {
            if (!current.get(i).sameAs(received.get(i))) {
                return false;
            }
        }
        return true;
    }

    // 정보 값 규칙을 차례로 검사한다. 정보 항목인지를 먼저 봐야 뒤 검사가 유형의 연결을 믿고 쓸 수 있다
    static void validate(List<InfoValueInfo> infoValues, ProductType productType) {
        validateInfoAttributes(infoValues, productType);
        validateInputTypes(infoValues);
        validateInfoValueCounts(infoValues);
        validateAllowedInfoValues(infoValues, productType);
        validateRequiredInfoValues(infoValues, productType);
    }

    // 값을 넣은 항목은 이 유형에 정보 항목으로 연결돼 있어야 한다. 유형에 없는 항목이나 옵션 항목은 거절한다
    private static void validateInfoAttributes(List<InfoValueInfo> infoValues, ProductType productType) {
        for (InfoValueInfo infoValue : infoValues) {
            Optional<ProductTypeAttribute> link = productType.findLink(infoValue.attribute());
            if (link.isEmpty() || link.get().getRole() != AttributeRole.INFO) {
                throw new BusinessException(ProductErrorCode.NOT_INFO_ATTRIBUTE);
            }
        }
    }

    // 직접 입력 항목은 글자만, 그 밖의 항목(팔레트 · 목록 · 다중 선택 목록)은 목록 값만 받는다
    private static void validateInputTypes(List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            boolean isText = infoValue.attribute().getInputType() == AttributeInputType.TEXT;
            if (isText && !infoValue.values().isEmpty()) {
                throw new BusinessException(ProductErrorCode.INPUT_TYPE_MISMATCH);
            }
            if (!isText && hasText(infoValue.text())) {
                throw new BusinessException(ProductErrorCode.INPUT_TYPE_MISMATCH);
            }
        }
    }

    private static void validateInfoValueCounts(List<InfoValueInfo> infoValues) {
        Set<Attribute> attributes = new HashSet<>();
        for (InfoValueInfo infoValue : infoValues) {
            // 같은 항목을 InfoValueInfo 여러 개로 나눠 보내면 거절한다 (INFO-01)
            if (!attributes.add(infoValue.attribute())) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_INFO_VALUE);
            }
            // 다중 선택 목록만 값을 여러 개 넣을 수 있다 (INFO-01)
            if (infoValue.attribute().getInputType() != AttributeInputType.MULTI_LIST
                && infoValue.values().size() > 1) {
                throw new BusinessException(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED);
            }
            // 한 항목 안에 같은 값이 두 번 들어오면 거절한다 (INFO-01)
            if (new HashSet<>(infoValue.values()).size() != infoValue.values().size()) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_INFO_VALUE);
            }
        }
    }

    // 목록 값은 그 항목의 값이어야 하고, 유형이 모든 값 허용이 아닌 항목이면 유형이 허용한 값이어야 한다 (INFO-03)
    private static void validateAllowedInfoValues(List<InfoValueInfo> infoValues, ProductType productType) {
        for (InfoValueInfo infoValue : infoValues) {
            for (AttributeValue value : infoValue.values()) {
                if (value.getAttribute() != infoValue.attribute()) {
                    throw new BusinessException(ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
                }
                if (!productType.allows(value)) {
                    throw new BusinessException(ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
                }
            }
        }
    }

    // 유형이 필수로 정한 정보 항목마다 값이 있어야 한다 (INFO-02)
    private static void validateRequiredInfoValues(List<InfoValueInfo> infoValues, ProductType productType) {
        for (ProductTypeAttribute link : productType.getAttributes()) {
            if (link.getRole() == AttributeRole.INFO
                && link.isRequired()
                && !hasValue(infoValues, link.getAttribute())) {
                throw new BusinessException(ProductErrorCode.REQUIRED_INFO_MISSING);
            }
        }
    }

    // 목록 항목은 values가, 직접 입력 항목은 text가 비어 있지 않아야 값이 있는 것이다
    private static boolean hasValue(List<InfoValueInfo> infoValues, Attribute attribute) {
        for (InfoValueInfo infoValue : infoValues) {
            if (infoValue.attribute() != attribute) {
                continue;
            }
            return !infoValue.values().isEmpty() || hasText(infoValue.text());
        }
        return false;
    }

    // 직접 입력한 글이 있는지 판단하는 기준이다. 공백만 있는 글은 없는 것으로 본다. 값 저장과 필수 항목 검사가 같은 기준을 쓴다
    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
