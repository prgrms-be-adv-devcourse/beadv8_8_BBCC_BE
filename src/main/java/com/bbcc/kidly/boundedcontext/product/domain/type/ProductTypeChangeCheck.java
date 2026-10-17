package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.List;

/**
 * 상품이 있는 유형은 기존 상품을 깨지 않는 변경만 받는다는 규칙을 검사한다.
 * 받는 변경은 허용 값 추가, 선택 정보 항목 추가, 필수 항목을 선택으로 바꾸기, 모든 값 허용으로 넓히기, 순서 변경뿐이다.
 */
final class ProductTypeChangeCheck {

    private ProductTypeChangeCheck() {
    }

    // 새 연결 목록이 기존 상품을 깨면 true를 돌려준다
    static boolean breaksProducts(
        List<ProductTypeAttribute> current, List<ProductTypeAllowedValue> currentAllowed,
        List<ProductType.LinkInfo> links) {
        for (ProductTypeAttribute link : current) {
            ProductType.LinkInfo info = findInfo(links, link.getAttribute());
            if (info == null || breaksLink(link, info, currentAllowed)) {
                return true;
            }
        }
        for (ProductType.LinkInfo info : links) {
            if (isNewLink(current, info) && (info.role() != AttributeRole.INFO || info.required())) {
                return true;
            }
        }
        return false;
    }

    private static boolean breaksLink(
        ProductTypeAttribute link, ProductType.LinkInfo info, List<ProductTypeAllowedValue> currentAllowed) {
        if (link.getRole() != info.role() || link.isSizeRole() != info.sizeRole()
            || link.isPriceAffecting() != info.priceAffecting()) {
            return true;
        }
        if (!link.isRequired() && info.required()) {
            return true;
        }
        if (link.isAllValuesAllowed() && !info.allValuesAllowed()) {
            return true;
        }
        return !info.allValuesAllowed() && dropsAllowedValue(link.getAttribute(), currentAllowed, info);
    }

    private static boolean dropsAllowedValue(
        Attribute attribute, List<ProductTypeAllowedValue> currentAllowed, ProductType.LinkInfo info) {
        for (ProductTypeAllowedValue allowed : currentAllowed) {
            AttributeValue value = allowed.getAttributeValue();
            if (value.getAttribute() == attribute && !info.allowedValues().contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static ProductType.LinkInfo findInfo(List<ProductType.LinkInfo> links, Attribute attribute) {
        for (ProductType.LinkInfo info : links) {
            if (info.attribute() == attribute) {
                return info;
            }
        }
        return null;
    }

    private static boolean isNewLink(List<ProductTypeAttribute> current, ProductType.LinkInfo info) {
        for (ProductTypeAttribute link : current) {
            if (link.getAttribute() == info.attribute()) {
                return false;
            }
        }
        return true;
    }
}
