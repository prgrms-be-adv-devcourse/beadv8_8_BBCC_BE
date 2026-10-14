package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.List;

final class ProductTypeFixture {

    private ProductTypeFixture() {
    }

    static Attribute color() {
        return Attribute.create("색상", "COLOR", AttributeInputType.PALETTE);
    }

    static Attribute size() {
        return Attribute.create("사이즈", "SIZE", AttributeInputType.LIST);
    }

    static Attribute shoeSize() {
        return Attribute.create("신발 사이즈", "SHOE_SIZE", AttributeInputType.LIST);
    }

    static Attribute mainMaterial() {
        return Attribute.create("주요소재", "MAIN_MATERIAL", AttributeInputType.LIST);
    }

    static Attribute fabricComposition() {
        return Attribute.create("혼용률", "FABRIC_COMPOSITION", AttributeInputType.TEXT);
    }

    // 기준 데이터의 의류: 색상 · 사이즈는 필수 옵션 항목, 주요소재는 필수 정보 항목, 혼용률은 선택 정보 항목
    static List<ProductType.LinkInfo> clothingLinks() {
        return List.of(
            requiredOption(color()),
            sizeOption(size()),
            requiredInfo(mainMaterial()),
            optionalTextInfo(fabricComposition()));
    }

    static ProductType.LinkInfo requiredOption(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.OPTION, true, true, false, false, List.of());
    }

    static ProductType.LinkInfo optionalOption(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.OPTION, false, true, false, false, List.of());
    }

    static ProductType.LinkInfo sizeOption(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.OPTION, true, true, true, false, List.of());
    }

    static ProductType.LinkInfo textOption(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.OPTION, true, false, false, false, List.of());
    }

    static ProductType.LinkInfo priceAffectingOption(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.OPTION, true, true, false, true, List.of());
    }

    static ProductType.LinkInfo priceAffectingInfo(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.INFO, true, true, false, true, List.of());
    }

    static ProductType.LinkInfo sizeInfo(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.INFO, true, true, true, false, List.of());
    }

    static ProductType.LinkInfo requiredInfo(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.INFO, true, true, false, false, List.of());
    }

    // 직접 입력 항목은 고를 값이 없어서 모든 값 허용을 false로 두고 허용 값도 연결하지 않는다
    static ProductType.LinkInfo optionalTextInfo(Attribute attribute) {
        return new ProductType.LinkInfo(attribute, AttributeRole.INFO, false, false, false, false, List.of());
    }
}
