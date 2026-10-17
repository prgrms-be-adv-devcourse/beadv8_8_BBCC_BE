package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.ArrayList;
import java.util.List;

/**
 * 상품이 있는 유형은 기존 상품을 깨지 않는 변경만 받는다는 규칙을 검사한다.
 * 받는 변경은 허용 값 추가, 선택 정보 항목 추가, 필수 항목을 선택으로 바꾸기, 모든 값 허용으로 넓히기,
 * 옵션 항목끼리의 순서는 그대로 두고 정보 항목의 순서를 바꾸는 것뿐이다.
 * 옵션 이름은 연결 순서로 만들어지므로, 옵션 항목끼리의 순서가 바뀌면 같은 상품 안에서 옵션 이름의 순서가 섞인다.
 */
final class ProductTypeChangeCheck {

    private ProductTypeChangeCheck() {
    }

    // 새 연결 목록이 기존 상품을 깨면 true를 돌려준다
    static boolean breaksProducts(ProductType type, List<ProductType.LinkInfo> links) {
        for (ProductTypeAttribute link : type.getAttributes()) {
            ProductType.LinkInfo info = findInfo(links, link.getAttribute());
            if (info == null || breaksLink(link, info, type.getAllowedValues())) {
                return true;
            }
        }
        for (ProductType.LinkInfo info : links) {
            if (type.findLink(info.attribute()).isEmpty()
                && (info.role() != AttributeRole.INFO || info.required())) {
                return true;
            }
        }
        return reordersOptions(type.getAttributes(), links);
    }

    // 옵션 항목끼리의 상대 순서가 바뀌었는지 본다. 정보 항목의 위치는 보지 않는다
    private static boolean reordersOptions(List<ProductTypeAttribute> current, List<ProductType.LinkInfo> links) {
        List<Attribute> currentOptions = new ArrayList<>();
        for (ProductTypeAttribute link : current) {
            if (link.getRole() == AttributeRole.OPTION) {
                currentOptions.add(link.getAttribute());
            }
        }
        List<Attribute> newOptions = new ArrayList<>();
        for (ProductType.LinkInfo info : links) {
            if (info.role() == AttributeRole.OPTION) {
                newOptions.add(info.attribute());
            }
        }
        return !currentOptions.equals(newOptions);
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
}
