package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 상품 유형의 연결 목록이 설정 규칙에 맞는지 검사한다(TYPE-02 · 03 · 04 · 06 · 08 · 13).
 * 같은 항목과 같은 허용 값은 한 번만 연결하고, 필수 옵션 항목이 하나 이상 있어야 한다.
 * 사이즈 역할과 가격 구분은 옵션 항목에만 둘 수 있고, 사이즈 역할은 항목 하나에만 둔다.
 * 옵션 항목은 값을 하나 고르는 입력 방식이어야 한다.
 * 모든 값을 허용하지 않는 항목(텍스트 제외)은 허용 값이 있어야 하고, 모든 값을 허용하는 항목은 허용 값이 없어야 하며,
 * 허용 값은 그 항목의 목록 값이어야 한다.
 */
final class ProductTypeLinkCheck {

    private ProductTypeLinkCheck() {
    }

    // 규칙에 어긋나면 그 규칙의 오류를 던진다
    static void requireValid(List<ProductType.LinkInfo> links) {
        if (hasDuplicatedAttribute(links)) {
            throw new BusinessException(ProductTypeErrorCode.DUPLICATE_ATTRIBUTE);
        }
        if (hasDuplicatedAllowedValue(links)) {
            throw new BusinessException(ProductTypeErrorCode.DUPLICATE_ALLOWED_VALUE);
        }
        if (!hasRequiredOption(links)) {
            throw new BusinessException(ProductTypeErrorCode.REQUIRED_OPTION_MISSING);
        }
        if (hasManySizeRoles(links)) {
            throw new BusinessException(ProductTypeErrorCode.MULTIPLE_SIZE_ROLES);
        }
        if (hasSizeRoleOnInfo(links)) {
            throw new BusinessException(ProductTypeErrorCode.SIZE_ROLE_ON_INFO);
        }
        if (hasPriceAffectingOnInfo(links)) {
            throw new BusinessException(ProductTypeErrorCode.PRICE_AFFECTING_ON_INFO);
        }
        if (hasNonSingleChoiceOption(links)) {
            throw new BusinessException(ProductTypeErrorCode.OPTION_NOT_SINGLE_CHOICE);
        }
        if (hasLinkWithoutAllowedValues(links)) {
            throw new BusinessException(ProductTypeErrorCode.ALLOWED_VALUES_MISSING);
        }
        if (hasAllowedValuesOnAllValuesAllowed(links)) {
            throw new BusinessException(ProductTypeErrorCode.ALLOWED_VALUES_ON_ALL_VALUES);
        }
        if (hasAllowedValueOfOtherAttribute(links)) {
            throw new BusinessException(ProductTypeErrorCode.ALLOWED_VALUE_OF_OTHER_ATTRIBUTE);
        }
    }

    private static boolean hasDuplicatedAttribute(List<ProductType.LinkInfo> links) {
        Set<Attribute> attributes = new HashSet<>();
        for (ProductType.LinkInfo link : links) {
            if (!attributes.add(link.attribute())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDuplicatedAllowedValue(List<ProductType.LinkInfo> links) {
        for (ProductType.LinkInfo link : links) {
            if (new HashSet<>(link.allowedValues()).size() != link.allowedValues().size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRequiredOption(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link -> link.role() == AttributeRole.OPTION && link.required());
    }

    private static boolean hasManySizeRoles(List<ProductType.LinkInfo> links) {
        return links.stream().filter(ProductType.LinkInfo::sizeRole).count() > 1;
    }

    private static boolean hasSizeRoleOnInfo(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link -> link.sizeRole() && link.role() != AttributeRole.OPTION);
    }

    private static boolean hasPriceAffectingOnInfo(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link -> link.priceAffecting() && link.role() != AttributeRole.OPTION);
    }

    private static boolean hasNonSingleChoiceOption(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link -> link.role() == AttributeRole.OPTION && !isSingleChoice(link));
    }

    private static boolean isSingleChoice(ProductType.LinkInfo link) {
        AttributeInputType inputType = link.attribute().getInputType();
        return inputType == AttributeInputType.PALETTE || inputType == AttributeInputType.LIST;
    }

    private static boolean hasLinkWithoutAllowedValues(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link ->
            link.attribute().getInputType() != AttributeInputType.TEXT
                && !link.allValuesAllowed()
                && link.allowedValues().isEmpty());
    }

    private static boolean hasAllowedValuesOnAllValuesAllowed(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link -> link.allValuesAllowed() && !link.allowedValues().isEmpty());
    }

    private static boolean hasAllowedValueOfOtherAttribute(List<ProductType.LinkInfo> links) {
        return links.stream().anyMatch(link ->
            link.allowedValues().stream().anyMatch(value -> value.getAttribute() != link.attribute()));
    }
}
